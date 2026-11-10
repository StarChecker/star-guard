package ru.itmo.ipkn.starguard.core.domain;

import java.time.Instant;
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
        StarIndex starIndex
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
        starIndex = starIndex == null ? StarIndex.empty() : starIndex;
    }

    /** Snapshot без глобального индекса звёзд: C4 в этом случае неактивна. */
    public RepoSnapshot(
            RepoRef repo,
            TimeWindow window,
            Instant capturedAt,
            EventCounts counts,
            int uniqueActors,
            int starActors,
            int[] starsByMinute,
            DataAvailability data) {
        this(repo, window, capturedAt, counts, uniqueActors, starActors, starsByMinute, data, StarIndex.empty());
    }

    @Override
    public int[] starsByMinute() {
        return starsByMinute.clone();
    }
}
