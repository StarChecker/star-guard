package ru.itmo.ipkn.starguard.core.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import ru.itmo.ipkn.starguard.core.scoring.CheckMode;

/** Настройки C1 «Всплеск звёзд» из scoring.yaml. */
@ConfigurationProperties(prefix = "app.scoring.checks.burst")
public record BurstProperties(
        CheckMode mode,
        Boolean enabled,
        Double ratioFloor,
        Double ratioSpan,
        Integer minBurst,
        Integer warmupMinutes,
        Double weight
) {
    public static final String MODE_PROPERTY = "app.scoring.checks.burst.mode";
    public static final String ENABLED_PROPERTY = "app.scoring.checks.burst.enabled";

    public BurstProperties {
        if (mode == null || enabled == null || ratioFloor == null || ratioSpan == null
                || minBurst == null || warmupMinutes == null || weight == null) {
            throw new IllegalArgumentException("Incomplete app.scoring.checks.burst configuration");
        }
        if (!Double.isFinite(ratioFloor) || ratioFloor < 0
                || !Double.isFinite(ratioSpan) || ratioSpan <= 0
                || minBurst < 1 || warmupMinutes < 1
                || !Double.isFinite(weight) || weight < 0 || weight > 1) {
            throw new IllegalArgumentException("Invalid app.scoring.checks.burst configuration");
        }
    }
}
