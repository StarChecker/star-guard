package ru.itmo.ipkn.starguard.core.domain;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Вход движка scoring. В PoC заполняется тем, что реально можно посчитать
 * из событий GH Archive; недостающие блоки помечены в {@link DataAvailability}.
 */
public record RepoSnapshot(
        RepoRef repo,
        TimeWindow window,
        Instant capturedAt,
        EventCounts counts,
        int uniqueActors,
        int starActors,
        int[] starsByMinute,
        DataAvailability data,
        List<ActorProfile> starActorProfiles
) {

    public RepoSnapshot {
        Objects.requireNonNull(repo, "repo");
        Objects.requireNonNull(window, "window");
        Objects.requireNonNull(capturedAt, "capturedAt");
        Objects.requireNonNull(counts, "counts");
        Objects.requireNonNull(data, "data");
        starsByMinute = starsByMinute == null
                ? new int[window.hours() * 60]
                : starsByMinute.clone();
        starActorProfiles = starActorProfiles == null ? List.of() : List.copyOf(starActorProfiles);
    }

    public RepoSnapshot(
            RepoRef repo,
            TimeWindow window,
            Instant capturedAt,
            EventCounts counts,
            int uniqueActors,
            int starActors,
            int[] starsByMinute,
            DataAvailability data) {
        this(repo, window, capturedAt, counts, uniqueActors, starActors, starsByMinute, data, List.of());
    }

    @Override
    public int[] starsByMinute() {
        return starsByMinute.clone();
    }
}
