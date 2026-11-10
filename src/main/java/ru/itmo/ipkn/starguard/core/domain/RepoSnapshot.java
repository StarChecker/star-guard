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
        DataAvailability data
) {

    public RepoSnapshot {
        Objects.requireNonNull(repo, "repo");
        Objects.requireNonNull(window, "window");
        Objects.requireNonNull(capturedAt, "capturedAt");
        Objects.requireNonNull(counts, "counts");
        Objects.requireNonNull(data, "data");
        starsByMinute = starsByMinute == null ? null : starsByMinute.clone();
    }

    @Override
    public int[] starsByMinute() {
        return starsByMinute == null ? null : starsByMinute.clone();
    }
}
