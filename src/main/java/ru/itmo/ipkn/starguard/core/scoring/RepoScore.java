package ru.itmo.ipkn.starguard.core.scoring;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/** Итог работы движка: score, уровень, confidence и explain по проверкам. */
public record RepoScore(
        int score,
        ScoreLevel level,
        double confidence,
        boolean insufficientData,
        List<CheckContribution> checks,
        boolean mocked,
        List<String> rulesApplied,
        Instant computedAt
) {

    public RepoScore {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(computedAt, "computedAt");
        checks = List.copyOf(Objects.requireNonNull(checks, "checks"));
        rulesApplied = List.copyOf(Objects.requireNonNull(rulesApplied, "rulesApplied"));
    }
}
