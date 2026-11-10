package ru.itmo.ipkn.starguard.core.scoring;

import java.util.Map;
import java.util.Objects;

/** Вклад проверки в итоговый score — основа explain. */
public record CheckContribution(
        String checkId,
        double score,
        double confidence,
        double weight,
        double contribution,
        boolean active,
        String error,
        Map<String, Object> details
) {

    public CheckContribution {
        Objects.requireNonNull(checkId, "checkId");
        details = details == null ? Map.of() : Map.copyOf(details);
    }
}
