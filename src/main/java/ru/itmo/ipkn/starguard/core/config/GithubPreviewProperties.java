package ru.itmo.ipkn.starguard.core.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Preview холодного репозитория по GitHub REST (F5): базовая статистика, пока полного score нет.
 * Токен необязателен ({@code app.github.token}); без него лимит GitHub — 60 запросов в час.
 *
 * @param enabled  включён ли {@code GET /api/v1/preview/...}
 * @param cacheTtl сколько хранить ответ (и «не найдено»)
 */
@ConfigurationProperties(prefix = "app.github.preview")
public record GithubPreviewProperties(Boolean enabled, Duration cacheTtl) {

    public GithubPreviewProperties {
        enabled = enabled == null || enabled;
        cacheTtl = cacheTtl == null ? Duration.ofMinutes(15) : cacheTtl;
        if (cacheTtl.isNegative() || cacheTtl.isZero()) {
            throw new IllegalArgumentException("app.github.preview.cache-ttl must be positive");
        }
    }

    public static GithubPreviewProperties defaults() {
        return new GithubPreviewProperties(null, null);
    }
}
