package ru.itmo.ipkn.starguard.core.github;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import feign.FeignException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import ru.itmo.ipkn.starguard.core.config.GithubPreviewProperties;
import ru.itmo.ipkn.starguard.core.config.GithubProperties;
import ru.itmo.ipkn.starguard.core.domain.RepoRef;

import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Preview холодного репозитория (F5): звёзды, форки, открытые issues, наблюдатели
 * и контрибьюторы из GitHub REST. Нужен, когда в GH Archive нет событий за окно,
 * а полный score ещё считается или недоступен.
 * <p>
 * Два запроса на репозиторий, кэш {@code app.github.preview.cache-ttl} (в том числе для 404).
 * При лимите GitHub (403/429) запросы приостанавливаются до {@code X-RateLimit-Reset},
 * а из кэша ответы продолжают отдаваться.
 */
@Service
public class GithubPreviewService {

    private static final Logger log = LoggerFactory.getLogger(GithubPreviewService.class);
    private static final Pattern LAST_PAGE = Pattern.compile("[?&]page=(\\d+)[^>]*>;\\s*rel=\"last\"");
    private static final int FORBIDDEN = 403;
    private static final int NOT_FOUND = 404;
    private static final int TOO_MANY_REQUESTS = 429;
    private static final int MAX_CACHE = 10_000;

    private final GithubReposFeignClient client;
    private final GithubProperties githubProperties;
    private final GithubPreviewProperties properties;
    private final Clock clock;
    private final Cache<String, Optional<RepoPreview>> cache;
    private volatile Instant pausedUntil = Instant.EPOCH;

    public GithubPreviewService(
            GithubReposFeignClient client,
            GithubProperties githubProperties,
            GithubPreviewProperties properties,
            Clock clock) {
        this.client = client;
        this.githubProperties = githubProperties;
        this.properties = properties;
        this.clock = clock;
        this.cache = Caffeine.newBuilder()
                .expireAfterWrite(properties.cacheTtl())
                .maximumSize(MAX_CACHE)
                .build();
    }

    /**
     * @throws RepoGoneException          репозитория нет на GitHub (удалён или приватный)
     * @throws GithubUnavailableException preview выключен, лимит GitHub или GitHub недоступен
     */
    public RepoPreview preview(RepoRef repo) {
        if (!properties.enabled()) {
            throw new GithubUnavailableException("GITHUB_PREVIEW_DISABLED", "GitHub preview is disabled");
        }
        String key = repo.fullName().toLowerCase(Locale.ROOT);
        Optional<RepoPreview> cached = cache.getIfPresent(key);
        if (cached != null) {
            return cached.map(RepoPreview::fromCache).orElseThrow(() -> new RepoGoneException(repo));
        }
        if (clock.instant().isBefore(pausedUntil)) {
            throw rateLimited();
        }

        GithubRepoInfo info;
        try {
            info = client.repo(repo.owner(), repo.name());
        } catch (FeignException e) {
            if (e.status() == NOT_FOUND) {
                cache.put(key, Optional.empty());
                throw new RepoGoneException(repo);
            }
            throw failure(e);
        } catch (RuntimeException e) {
            throw new GithubUnavailableException("GITHUB_UNAVAILABLE", "GitHub API request failed", e);
        }
        if (info == null) {
            throw new GithubUnavailableException("GITHUB_UNAVAILABLE", "GitHub API returned no repository");
        }

        RepoRef canonical = canonical(repo, info.fullName());
        RepoPreview preview = new RepoPreview(
                canonical,
                info.description(),
                info.htmlUrl(),
                orZero(info.stargazersCount()),
                orZero(info.forksCount()),
                orZero(info.openIssuesCount()),
                info.subscribersCount(),
                contributors(canonical),
                Boolean.TRUE.equals(info.archived()),
                Boolean.TRUE.equals(info.fork()),
                info.createdAt(),
                info.pushedAt(),
                clock.instant(),
                false);
        cache.put(key, Optional.of(preview));
        return preview;
    }

    /** Число контрибьюторов по заголовку Link; null — GitHub не отдал (большие репозитории, лимит, ошибка). */
    private Integer contributors(RepoRef repo) {
        try {
            ResponseEntity<List<Map<String, Object>>> response =
                    client.contributors(repo.owner(), repo.name(), 1, "1");
            if (response.getStatusCode().value() == HttpStatus.NO_CONTENT.value()) {
                return 0; // пустой репозиторий
            }
            Integer fromLink = lastPage(response.getHeaders().get("Link"));
            if (fromLink != null) {
                return fromLink;
            }
            List<Map<String, Object>> body = response.getBody();
            return body == null ? 0 : body.size();
        } catch (FeignException e) {
            if (e.status() == FORBIDDEN || e.status() == TOO_MANY_REQUESTS) {
                if (isRateLimit(e)) {
                    pause(e);
                }
            }
            log.debug("GitHub contributors lookup failed for {}: {}", repo.fullName(), e.getMessage());
            return null;
        } catch (RuntimeException e) {
            log.debug("GitHub contributors lookup failed for {}: {}", repo.fullName(), e.getMessage());
            return null;
        }
    }

    static Integer lastPage(Collection<String> linkHeaders) {
        if (linkHeaders == null) {
            return null;
        }
        for (String header : linkHeaders) {
            Matcher matcher = LAST_PAGE.matcher(header);
            if (matcher.find()) {
                return Integer.parseInt(matcher.group(1));
            }
        }
        return null;
    }

    private GithubUnavailableException failure(FeignException e) {
        if ((e.status() == FORBIDDEN || e.status() == TOO_MANY_REQUESTS) && isRateLimit(e)) {
            pause(e);
            return rateLimited();
        }
        if (e.status() == 401) {
            return new GithubUnavailableException("GITHUB_TOKEN_INVALID", "GitHub rejected the token", e);
        }
        return new GithubUnavailableException("GITHUB_UNAVAILABLE", "GitHub API request failed: HTTP " + e.status(), e);
    }

    private GithubUnavailableException rateLimited() {
        String hint = githubProperties.token().isEmpty()
                ? " Set GITHUB_TOKEN to raise the limit from 60 to 5000 requests per hour."
                : "";
        return new GithubUnavailableException("GITHUB_RATE_LIMITED",
                "GitHub API rate limit reached until " + pausedUntil + "." + hint);
    }

    /** 429 — всегда лимит; 403 — лимит, если GitHub так говорит (иначе это, например, запрет доступа). */
    private static boolean isRateLimit(FeignException e) {
        if (e.status() == TOO_MANY_REQUESTS) {
            return true;
        }
        Map<String, Collection<String>> headers = e.responseHeaders();
        boolean noneLeft = header(headers, "x-ratelimit-remaining").map("0"::equals).orElse(false);
        String body = e.contentUTF8();
        return noneLeft || (body != null && body.toLowerCase(Locale.ROOT).contains("rate limit"));
    }

    private void pause(FeignException e) {
        Instant until = header(e.responseHeaders(), "x-ratelimit-reset")
                .flatMap(GithubPreviewService::epochSeconds)
                .orElse(clock.instant().plus(githubProperties.rateLimitPause()));
        pausedUntil = until;
        log.warn("GitHub API rate limit hit (HTTP {}), preview paused until {}", e.status(), until);
    }

    private static Optional<String> header(Map<String, Collection<String>> headers, String name) {
        if (headers == null) {
            return Optional.empty();
        }
        return headers.entrySet().stream()
                .filter(entry -> name.equalsIgnoreCase(entry.getKey()))
                .flatMap(entry -> entry.getValue().stream())
                .findFirst()
                .map(String::trim);
    }

    private static Optional<Instant> epochSeconds(String value) {
        try {
            return Optional.of(Instant.ofEpochSecond(Long.parseLong(value)));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    /** GitHub отвечает и по старому имени после переименования: берём актуальное. */
    private static RepoRef canonical(RepoRef requested, String fullName) {
        if (fullName == null || !fullName.contains("/")) {
            return requested;
        }
        String[] parts = fullName.split("/", 2);
        return new RepoRef(parts[0], parts[1]);
    }

    private static int orZero(Integer value) {
        return value == null ? 0 : value;
    }
}
