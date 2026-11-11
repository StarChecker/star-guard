package ru.itmo.ipkn.starguard.core.stats;

import ru.itmo.ipkn.starguard.core.domain.ActorActivity;
import ru.itmo.ipkn.starguard.core.domain.RepoSnapshot;
import ru.itmo.ipkn.starguard.core.domain.RepoStats;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Результат агрегации: публичная статистика и snapshot для scoring. */
public record RepoAggregate(RepoStats stats, RepoSnapshot snapshot, List<ActorActivity> starActors) {

    public RepoAggregate {
        Objects.requireNonNull(stats, "stats");
        Objects.requireNonNull(snapshot, "snapshot");
        starActors = starActors == null ? List.of() : List.copyOf(starActors);
    }

    public RepoAggregate(RepoStats stats, RepoSnapshot snapshot) {
        this(stats, snapshot, List.of());
    }

    public RepoAggregate withAccountCreatedAt(Map<Long, Instant> createdAtById) {
        if (createdAtById.isEmpty()) {
            return this;
        }
        List<ActorActivity> enriched = ActorActivity.withAccountCreatedAt(starActors, createdAtById);
        return new RepoAggregate(
                stats, snapshot.withStarActorProfiles(ActorActivity.profiles(enriched)), enriched);
    }
}
