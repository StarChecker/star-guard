package ru.itmo.ipkn.starguard.core.domain;

import java.util.List;
import java.util.Objects;

/** Публичная статистика по репозиторию — то, что отдаётся в API. */
public record RepoStats(
        RepoRef repo,
        TimeWindow window,
        EventCounts counts,
        int uniqueActors,
        int starActors,
        int peakStars10m,
        List<HourBucket> starsByHour
) {

    public RepoStats {
        Objects.requireNonNull(repo, "repo");
        Objects.requireNonNull(window, "window");
        Objects.requireNonNull(counts, "counts");
        starsByHour = List.copyOf(Objects.requireNonNull(starsByHour, "starsByHour"));
    }
}
