package ru.itmo.ipkn.starguard.core.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import ru.itmo.ipkn.starguard.core.scoring.CheckIds;
import ru.itmo.ipkn.starguard.core.scoring.MockMode;

import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Конфигурация scoring: веса проверок, порог confidence, правила усиления и уровни.
 * Все пороги живут здесь, в коде — никаких магических чисел.
 */
@ConfigurationProperties(prefix = "app.scoring")
public record ScoringProperties(
        MockMode mockMode,
        Double minConfidence,
        Map<String, Double> weights,
        Amplification amplification,
        LevelThresholds levels
) {

    public ScoringProperties {
        mockMode = mockMode == null ? MockMode.HEALTHY : mockMode;
        minConfidence = minConfidence == null ? 0.5 : minConfidence;
        if (minConfidence < 0.0 || minConfidence > 1.0) {
            throw new IllegalArgumentException("app.scoring.min-confidence must be within [0, 1]");
        }
        weights = weights == null ? Map.of() : Map.copyOf(weights);
        weights.forEach((id, weight) -> {
            if (id == null || id.isBlank() || weight == null || weight < 0.0) {
                throw new IllegalArgumentException("app.scoring.weights must contain non-negative values");
            }
        });
        amplification = amplification == null ? Amplification.defaults() : amplification;
        levels = levels == null ? LevelThresholds.defaults() : levels;
    }

    public double weight(String checkId) {
        return weights.getOrDefault(checkId, 0.0);
    }

    public record Amplification(MultiTrigger multiTrigger, StrongCheck strongCheck) {

        public Amplification {
            multiTrigger = multiTrigger == null ? new MultiTrigger(2, 0.7, 60) : multiTrigger;
            strongCheck = strongCheck == null ? StrongCheck.defaults() : strongCheck;
        }

        public static Amplification defaults() {
            return new Amplification(null, null);
        }
    }

    public record MultiTrigger(int minChecks, double checkScoreAtLeast, int floor) {

        public MultiTrigger {
            if (minChecks < 1) {
                throw new IllegalArgumentException("amplification.multi-trigger.min-checks must be >= 1");
            }
            if (checkScoreAtLeast < 0.0 || checkScoreAtLeast > 1.0) {
                throw new IllegalArgumentException("amplification.multi-trigger.check-score-at-least must be within [0, 1]");
            }
            if (floor < 0 || floor > 100) {
                throw new IllegalArgumentException("amplification.multi-trigger.floor must be within [0, 100]");
            }
        }
    }

    public record StrongCheck(
            Set<String> checkIds,
            double checkScoreAtLeast,
            double minConfidence,
            int floor
    ) {

        public StrongCheck {
            checkIds = checkIds == null || checkIds.isEmpty()
                    ? Set.of(CheckIds.BURST, CheckIds.FRESH_ACCOUNTS, CheckIds.ACTOR_OVERLAP)
                    : Set.copyOf(checkIds);
            if (checkScoreAtLeast < 0.0 || checkScoreAtLeast > 1.0) {
                throw new IllegalArgumentException("amplification.strong-check.check-score-at-least must be within [0, 1]");
            }
            if (minConfidence < 0.0 || minConfidence > 1.0) {
                throw new IllegalArgumentException("amplification.strong-check.min-confidence must be within [0, 1]");
            }
            if (floor < 0 || floor > 100) {
                throw new IllegalArgumentException("amplification.strong-check.floor must be within [0, 100]");
            }
        }

        public static StrongCheck defaults() {
            return new StrongCheck(null, 0.85, 0.7, 70);
        }
    }

    public record LevelThresholds(int medium, int high, int critical) {

        public LevelThresholds {
            boolean ascending = medium >= 1 && medium < high && high < critical && critical <= 100;
            if (!ascending) {
                throw new IllegalArgumentException(
                        "app.scoring.levels must satisfy 0 < medium < high < critical <= 100");
            }
        }

        public static LevelThresholds defaults() {
            return new LevelThresholds(30, 60, 85);
        }
    }
}
