package ru.itmo.ipkn.starguard.core.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

/** Настройки клиента GH Archive и окна анализа. */
@ConfigurationProperties(prefix = "app.archive")
public record ArchiveProperties(
        String baseUrl,
        Mode mode,
        int defaultWindowHours,
        int maxWindowHours,
        int lagHours,
        int parallelism,
        int retries,
        boolean failFast,
        Duration cacheTtl,
        String fixtureLocation,
        Instant windowEnd
) {

    public enum Mode {
        HTTP,
        FIXTURE
    }

    public ArchiveProperties {
        baseUrl = baseUrl == null || baseUrl.isBlank()
                ? "https://data.gharchive.org"
                : baseUrl;
        mode = mode == null ? Mode.HTTP : mode;
        if (defaultWindowHours < 1) {
            throw new IllegalArgumentException("app.archive.default-window-hours must be >= 1");
        }
        if (maxWindowHours < defaultWindowHours || maxWindowHours > 24) {
            throw new IllegalArgumentException(
                    "app.archive.max-window-hours must be within [default-window-hours, 24]");
        }
        if (lagHours < 0) {
            throw new IllegalArgumentException("app.archive.lag-hours must be >= 0");
        }
        if (parallelism < 1) {
            throw new IllegalArgumentException("app.archive.parallelism must be >= 1");
        }
        if (retries < 0) {
            throw new IllegalArgumentException("app.archive.retries must be >= 0");
        }
        cacheTtl = cacheTtl == null ? Duration.ofHours(1) : cacheTtl;
        fixtureLocation = fixtureLocation == null || fixtureLocation.isBlank()
                ? "classpath:fixtures/gharchive/*.jsonl"
                : fixtureLocation;
        windowEnd = windowEnd == null ? null : windowEnd.truncatedTo(ChronoUnit.HOURS);
    }
}
