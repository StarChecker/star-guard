package ru.itmo.ipkn.starguard.core.github;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import feign.FeignException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import ru.itmo.ipkn.starguard.core.config.GithubProperties;
import ru.itmo.ipkn.starguard.core.domain.ActorActivity;
import ru.itmo.ipkn.starguard.core.stats.RepoAggregate;

import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
public class AccountAgeEnricher {

    private static final Logger log = LoggerFactory.getLogger(AccountAgeEnricher.class);
    private static final int NOT_FOUND = 404;
    private static final int FORBIDDEN = 403;
    private static final int TOO_MANY_REQUESTS = 429;

    private final GithubUsersFeignClient client;
    private final GithubProperties properties;
    private final Clock clock;
    private final Cache<Long, Optional<Instant>> cache;
    private volatile Instant pausedUntil = Instant.MIN;

    public AccountAgeEnricher(GithubUsersFeignClient client, GithubProperties properties, Clock clock) {
        this.client = client;
        this.properties = properties;
        this.clock = clock;
        this.cache = Caffeine.newBuilder()
                .expireAfterWrite(properties.cacheTtl())
                .maximumSize(properties.cacheMaxSize())
                .build();
    }

    public RepoAggregate enrich(RepoAggregate aggregate, Integer starRank) {
        if (!properties.enabled()) {
            return aggregate;
        }
        if (starRank != null && (starRank < 1 || starRank > properties.topN())) {
            return aggregate;
        }
        return aggregate.withAccountCreatedAt(lookup(aggregate.starActors()));
    }

    Map<Long, Instant> lookup(List<ActorActivity> actors) {
        Map<Long, Instant> found = new HashMap<>();
        int requests = 0;
        for (ActorActivity actor : actors) {
            if (actor.accountCreatedAt() != null || actor.login().isBlank()) {
                continue;
            }
            Optional<Instant> createdAt = cache.getIfPresent(actor.actorId());
            if (createdAt == null) {
                if (requests >= properties.maxLookupsPerRequest() || paused()) {
                    continue;
                }
                requests++;
                createdAt = fetch(actor.login());
                if (createdAt == null) {
                    continue;
                }
                cache.put(actor.actorId(), createdAt);
            }
            createdAt.ifPresent(at -> found.put(actor.actorId(), at));
        }
        return found;
    }

    private Optional<Instant> fetch(String login) {
        try {
            GithubUser user = client.user(login);
            return Optional.ofNullable(user == null ? null : user.createdAt());
        } catch (FeignException e) {
            if (e.status() == NOT_FOUND) {
                return Optional.empty();
            }
            if (e.status() == FORBIDDEN || e.status() == TOO_MANY_REQUESTS) {
                pause(e);
                return null;
            }
            log.debug("GitHub user lookup failed for {}: {}", login, e.getMessage());
            return null;
        } catch (RuntimeException e) {
            log.debug("GitHub user lookup failed for {}: {}", login, e.getMessage());
            return null;
        }
    }

    private boolean paused() {
        return clock.instant().isBefore(pausedUntil);
    }

    private void pause(FeignException e) {
        Instant until = resetAt(e.responseHeaders())
                .orElse(clock.instant().plus(properties.rateLimitPause()));
        pausedUntil = until;
        log.warn("GitHub API rate limit hit (HTTP {}), account lookups paused until {}", e.status(), until);
    }

    private static Optional<Instant> resetAt(Map<String, Collection<String>> headers) {
        if (headers == null) {
            return Optional.empty();
        }
        return headers.entrySet().stream()
                .filter(entry -> "x-ratelimit-reset".equalsIgnoreCase(entry.getKey()))
                .flatMap(entry -> entry.getValue().stream())
                .findFirst()
                .flatMap(AccountAgeEnricher::epochSeconds);
    }

    private static Optional<Instant> epochSeconds(String value) {
        try {
            return Optional.of(Instant.ofEpochSecond(Long.parseLong(value.trim())));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }
}
