package ru.itmo.ipkn.starguard.core.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import ru.itmo.ipkn.starguard.core.scoring.CheckMode;

/**
 * Настройки C3 «Дисбаланс звёзды/активность».
 *
 * @param mode                  mock | real; по умолчанию наследует {@code app.scoring.mode}
 * @param engagementThreshold   engagement, при котором и выше которого вклад C3 равен нулю
 * @param starsGate             число звёзд, начиная с которого gate = 1
 * @param minStarsForConfidence меньше звёзд — данных недостаточно, проверка неактивна
 * @param confidence            confidence проверки, когда звёзд достаточно
 */
@ConfigurationProperties(prefix = "app.scoring.checks.engagement")
public record EngagementProperties(
        CheckMode mode,
        Double engagementThreshold,
        Integer starsGate,
        Integer minStarsForConfidence,
        Double confidence
) {

    /** Свойство, по которому выбирается реализация C3 (реальная или мок). */
    public static final String MODE_PROPERTY = "app.scoring.checks.engagement.mode";

    public EngagementProperties {
        mode = mode == null ? CheckMode.MOCK : mode;
        engagementThreshold = engagementThreshold == null ? 0.2 : engagementThreshold;
        starsGate = starsGate == null ? 20 : starsGate;
        minStarsForConfidence = minStarsForConfidence == null ? 5 : minStarsForConfidence;
        confidence = confidence == null ? 0.9 : confidence;

        if (engagementThreshold <= 0.0) {
            throw new IllegalArgumentException("app.scoring.checks.engagement.engagement-threshold must be > 0");
        }
        if (starsGate < 1) {
            throw new IllegalArgumentException("app.scoring.checks.engagement.stars-gate must be >= 1");
        }
        if (minStarsForConfidence < 0) {
            throw new IllegalArgumentException("app.scoring.checks.engagement.min-stars-for-confidence must be >= 0");
        }
        if (confidence < 0.0 || confidence > 1.0) {
            throw new IllegalArgumentException("app.scoring.checks.engagement.confidence must be within [0, 1]");
        }
    }

    public static EngagementProperties defaults() {
        return new EngagementProperties(null, null, null, null, null);
    }
}
