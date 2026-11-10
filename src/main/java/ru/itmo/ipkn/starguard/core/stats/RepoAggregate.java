package ru.itmo.ipkn.starguard.core.stats;

import ru.itmo.ipkn.starguard.core.domain.RepoSnapshot;
import ru.itmo.ipkn.starguard.core.domain.RepoStats;

import java.util.Objects;

/** Результат агрегации: публичная статистика и snapshot для scoring. */
public record RepoAggregate(RepoStats stats, RepoSnapshot snapshot) {

    public RepoAggregate {
        Objects.requireNonNull(stats, "stats");
        Objects.requireNonNull(snapshot, "snapshot");
    }
}
