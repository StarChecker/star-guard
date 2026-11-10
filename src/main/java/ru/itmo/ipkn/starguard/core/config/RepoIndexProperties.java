package ru.itmo.ipkn.starguard.core.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Предвычисленный индекс топ-N репозиториев по звёздам за окно.
 * Позволяет отвечать на запросы без скачивания и разбора дампов GH Archive.
 */
@ConfigurationProperties(prefix = "app.index")
public record RepoIndexProperties(
        Boolean enabled,
        Integer topN,
        Integer windowHours,
        Boolean buildOnStartup,
        String refreshCron
) {

    public RepoIndexProperties {
        enabled = enabled == null ? Boolean.TRUE : enabled;
        topN = topN == null ? 1000 : topN;
        windowHours = windowHours == null ? 6 : windowHours;
        buildOnStartup = buildOnStartup == null ? Boolean.TRUE : buildOnStartup;
        refreshCron = refreshCron == null || refreshCron.isBlank()
                ? "0 10 * * * *"
                : refreshCron;
        if (topN < 1) {
            throw new IllegalArgumentException("app.index.top-n must be >= 1");
        }
        if (windowHours < 1 || windowHours > 24) {
            throw new IllegalArgumentException("app.index.window-hours must be within [1, 24]");
        }
    }
}
