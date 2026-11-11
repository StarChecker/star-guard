package ru.itmo.ipkn.starguard.core.github;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import ru.itmo.ipkn.starguard.core.config.ArchiveProperties;
import ru.itmo.ipkn.starguard.core.config.GithubHistoryProperties;
import ru.itmo.ipkn.starguard.core.config.GithubProperties;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Существует ли репозиторий на GitHub. Верх топа по звёздам за несколько часов часто занимают
 * репозитории накрутки, которые GitHub уже удалил вместе с бот-аккаунтами: GH Archive их помнит,
 * а github.com отвечает 404. Такие репозитории не показываем пользователю как «что посмотреть».
 * <p>
 * С токеном — пакетно через GraphQL (50 репозиториев за запрос), без токена — HEAD к github.com.
 * Результат кэшируется. В офлайн-режиме фикстур проверка отключена.
 */
@Service
public class RepoExistenceService {

    private static final Logger log = LoggerFactory.getLogger(RepoExistenceService.class);
    private static final int BATCH = 50;
    private static final int MAX_HEAD_CHECKS = 200;
    private static final Pattern NAME = Pattern.compile("^[A-Za-z0-9_.-]{1,100}/[A-Za-z0-9_.-]{1,100}$");

    private final GithubGraphqlFeignClient graphql;
    private final GithubProperties githubProperties;
    private final GithubHistoryProperties properties;
    private final ArchiveProperties archiveProperties;
    private final WebProbe webProbe;
    private final Cache<String, Boolean> cache;

    @Autowired
    public RepoExistenceService(
            GithubGraphqlFeignClient graphql,
            GithubProperties githubProperties,
            GithubHistoryProperties properties,
            ArchiveProperties archiveProperties) {
        this(graphql, githubProperties, properties, archiveProperties, new HttpWebProbe());
    }

    RepoExistenceService(
            GithubGraphqlFeignClient graphql,
            GithubProperties githubProperties,
            GithubHistoryProperties properties,
            ArchiveProperties archiveProperties,
            WebProbe webProbe) {
        this.graphql = graphql;
        this.githubProperties = githubProperties;
        this.properties = properties;
        this.archiveProperties = archiveProperties;
        this.webProbe = webProbe;
        this.cache = Caffeine.newBuilder()
                .expireAfterWrite(properties.existenceTtl())
                .maximumSize(100_000)
                .build();
    }

    public boolean enabled() {
        return properties.existenceCheck() && archiveProperties.mode() != ArchiveProperties.Mode.FIXTURE;
    }

    /**
     * Проверяет репозитории; в ответе только те, про которые удалось узнать
     * (true — существует, false — удалён/скрыт). Неизвестные — отсутствуют.
     */
    public Map<String, Boolean> check(List<String> fullNames) {
        Map<String, Boolean> result = new HashMap<>();
        List<String> unknown = new ArrayList<>();
        for (String name : fullNames) {
            if (!NAME.matcher(name).matches()) {
                continue;
            }
            Boolean cached = cache.getIfPresent(key(name));
            if (cached != null) {
                result.put(name, cached);
            } else {
                unknown.add(name);
            }
        }
        if (unknown.isEmpty()) {
            return result;
        }
        if (!githubProperties.token().isEmpty()) {
            for (int i = 0; i < unknown.size(); i += BATCH) {
                result.putAll(viaGraphql(unknown.subList(i, Math.min(unknown.size(), i + BATCH))));
            }
        } else {
            for (String name : unknown.subList(0, Math.min(unknown.size(), MAX_HEAD_CHECKS))) {
                webProbe.exists(name).ifPresent(exists -> result.put(name, exists));
            }
        }
        unknown.forEach(name -> {
            Boolean found = result.get(name);
            if (found != null) {
                cache.put(key(name), found);
            }
        });
        return result;
    }

    private Map<String, Boolean> viaGraphql(List<String> names) {
        StringBuilder declarations = new StringBuilder();
        StringBuilder fields = new StringBuilder();
        Map<String, Object> variables = new LinkedHashMap<>();
        for (int i = 0; i < names.size(); i++) {
            String[] parts = names.get(i).split("/", 2);
            declarations.append(i == 0 ? "" : ", ").append("$o").append(i).append(": String!, $n").append(i).append(": String!");
            fields.append(" r").append(i).append(": repository(owner: $o").append(i).append(", name: $n").append(i).append(") { id }");
            variables.put("o" + i, parts[0]);
            variables.put("n" + i, parts[1]);
        }
        String query = "query(" + declarations + ") {" + fields + " }";
        Map<String, Boolean> result = new HashMap<>();
        try {
            Map<String, Object> response = graphql.query(Map.of("query", query, "variables", variables));
            Map<String, Object> data = Graphql.map(response, "data");
            if (data == null) {
                return result;
            }
            for (int i = 0; i < names.size(); i++) {
                result.put(names.get(i), data.get("r" + i) != null);
            }
        } catch (RuntimeException e) {
            log.debug("GitHub existence check failed: {}", e.getMessage());
        }
        return result;
    }

    private static String key(String name) {
        return name.toLowerCase(Locale.ROOT);
    }

    /** Проверка через github.com без токена. */
    interface WebProbe {
        Optional<Boolean> exists(String fullName);
    }

    static final class HttpWebProbe implements WebProbe {

        private final HttpClient http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();

        @Override
        public Optional<Boolean> exists(String fullName) {
            try {
                HttpRequest request = HttpRequest.newBuilder(URI.create("https://github.com/" + fullName))
                        .method("HEAD", HttpRequest.BodyPublishers.noBody())
                        .header("User-Agent", "star-guard")
                        .timeout(Duration.ofSeconds(10))
                        .build();
                int status = http.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
                if (status == 404 || status == 451) {
                    return Optional.of(false);
                }
                if (status >= 200 && status < 400) {
                    return Optional.of(true); // 301 — переименован, но существует
                }
                return Optional.empty();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return Optional.empty();
            } catch (Exception e) {
                return Optional.empty();
            }
        }
    }
}
