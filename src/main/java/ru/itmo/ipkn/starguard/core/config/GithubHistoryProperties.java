package ru.itmo.ipkn.starguard.core.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Анализ любого репозитория по истории GitHub (GraphQL) и проверка, что репозиторий существует.
 *
 * @param maxDays          глубина истории в днях
 * @param maxStars         сколько последних звёзд загружать (по 100 за запрос)
 * @param existenceCheck   скрывать из списка индекса удалённые на GitHub репозитории
 * @param existenceTtl     сколько помнить результат проверки существования
 */
@ConfigurationProperties(prefix = "app.github.history")
public record GithubHistoryProperties(
        Integer maxDays,
        Integer maxStars,
        Boolean existenceCheck,
        Duration existenceTtl
) {

    public GithubHistoryProperties {
        maxDays = maxDays == null ? 30 : maxDays;
        maxStars = maxStars == null ? 3000 : maxStars;
        existenceCheck = existenceCheck == null || existenceCheck;
        existenceTtl = existenceTtl == null ? Duration.ofHours(6) : existenceTtl;
        if (maxDays < 1 || maxDays > 90) {
            throw new IllegalArgumentException("app.github.history.max-days must be within [1, 90]");
        }
        if (maxStars < 100 || maxStars > 20_000) {
            throw new IllegalArgumentException("app.github.history.max-stars must be within [100, 20000]");
        }
        if (existenceTtl.isNegative() || existenceTtl.isZero()) {
            throw new IllegalArgumentException("app.github.history.existence-ttl must be positive");
        }
    }

    public static GithubHistoryProperties defaults() {
        return new GithubHistoryProperties(null, null, null, null);
    }
}
