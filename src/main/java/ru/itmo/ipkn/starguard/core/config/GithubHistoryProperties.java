package ru.itmo.ipkn.starguard.core.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "app.github.history")
public record GithubHistoryProperties(
        Integer maxDays,
        Integer historyDays,
        Integer maxForkPages,
        Duration cacheTtl,
        Boolean existenceCheck,
        Duration existenceTtl
) {

    public GithubHistoryProperties {
        maxDays = maxDays == null ? 30 : maxDays;
        historyDays = historyDays == null ? 180 : historyDays;
        maxForkPages = maxForkPages == null ? 10 : maxForkPages;
        cacheTtl = cacheTtl == null ? Duration.ofMinutes(5) : cacheTtl;
        existenceCheck = existenceCheck == null || existenceCheck;
        existenceTtl = existenceTtl == null ? Duration.ofHours(6) : existenceTtl;
        if (maxDays < 1 || maxDays > 3650) {
            throw new IllegalArgumentException("app.github.history.max-days must be within [1, 3650]");
        }
        if (historyDays < 1 || historyDays > 3650) {
            throw new IllegalArgumentException("app.github.history.history-days must be within [1, 3650]");
        }
        if (maxForkPages < 1 || maxForkPages > 100) {
            throw new IllegalArgumentException("app.github.history.max-fork-pages must be within [1, 100]");
        }
        if (cacheTtl.isNegative()) {
            throw new IllegalArgumentException("app.github.history.cache-ttl must not be negative");
        }
        if (existenceTtl.isNegative() || existenceTtl.isZero()) {
            throw new IllegalArgumentException("app.github.history.existence-ttl must be positive");
        }
    }

    public static GithubHistoryProperties defaults() {
        return new GithubHistoryProperties(null, null, null, null, null, null);
    }
}
