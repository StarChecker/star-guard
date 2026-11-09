package ru.itmo.ipkn.starguard.core.scoring;

import java.util.Map;
import java.util.Objects;

/** Результат одной проверки: нормализованный скор, полнота данных и детали для explain. */
public record CheckResult(
        String checkId,
        double score,
        double confidence,
        Map<String, Object> details
) {

    public CheckResult {
        Objects.requireNonNull(checkId, "checkId");
        score = clamp01(score);
        confidence = clamp01(confidence);
        details = details == null ? Map.of() : Map.copyOf(details);
    }

    private static double clamp01(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return 0.0;
        }
        return Math.clamp(value, 0.0, 1.0);
    }
}
