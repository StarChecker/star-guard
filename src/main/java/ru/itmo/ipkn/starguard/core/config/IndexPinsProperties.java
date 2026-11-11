package ru.itmo.ipkn.starguard.core.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Закрепление репозиториев в индексе через {@code PUT/POST /api/v1/index/repos} (F4-2).
 *
 * @param maxPinned сколько репозиториев можно закрепить; эндпоинт открытый, реестр в памяти
 */
@ConfigurationProperties(prefix = "app.index.pins")
public record IndexPinsProperties(Integer maxPinned) {

    public IndexPinsProperties {
        maxPinned = maxPinned == null ? 1000 : maxPinned;
        if (maxPinned < 0) {
            throw new IllegalArgumentException("app.index.pins.max-pinned must be >= 0");
        }
    }
}
