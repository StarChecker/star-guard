package ru.itmo.ipkn.starguard.core.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Сбор глобального индекса звёзд ({@code WatchEvent} всех репозиториев) при чтении дампов.
 * Индекс нужен C4 «Пересечение акторов».
 *
 * @param enabled         собирать ли индекс; выключение делает C4 неактивной
 * @param maxPairsPerHour лимит пар (репозиторий, актор) на один часовой дамп — ограничивает память;
 *                        при превышении индекс часа обрезается и C4 снижает confidence
 * @param cacheHours      сколько часовых индексов держать в кэше
 */
@ConfigurationProperties(prefix = "app.archive.star-index")
public record StarIndexProperties(
        Boolean enabled,
        Long maxPairsPerHour,
        Integer cacheHours
) {

    public StarIndexProperties {
        enabled = enabled == null ? Boolean.TRUE : enabled;
        maxPairsPerHour = maxPairsPerHour == null ? 500_000L : maxPairsPerHour;
        cacheHours = cacheHours == null ? 48 : cacheHours;
        if (maxPairsPerHour < 1) {
            throw new IllegalArgumentException("app.archive.star-index.max-pairs-per-hour must be >= 1");
        }
        if (cacheHours < 1) {
            throw new IllegalArgumentException("app.archive.star-index.cache-hours must be >= 1");
        }
    }

    public static StarIndexProperties defaults() {
        return new StarIndexProperties(null, null, null);
    }
}
