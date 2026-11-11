package ru.itmo.ipkn.starguard.core.domain;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Вход движка scoring. В PoC заполняется тем, что реально можно посчитать
 * из событий GH Archive; недостающие блоки помечены в {@link DataAvailability}.
 *
 * @param starActorProfiles профили акторов-звёзд для C2 (пустой список, если не собирались)
 * @param starIndex         глобальный индекс звёзд окна для C4 (пустой, если не собирался)
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
        List<ActorProfile> starActorProfiles,
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
        starActorProfiles = starActorProfiles == null ? List.of() : List.copyOf(starActorProfiles);
        starIndex = starIndex == null ? StarIndex.empty() : starIndex;
    }

    /** Snapshot без профилей акторов (C2 неактивна) и без индекса звёзд (C4 неактивна). */
    public RepoSnapshot(
            RepoRef repo,
            TimeWindow window,
            Instant capturedAt,
            EventCounts counts,
            int uniqueActors,
            int starActors,
            int[] starsByMinute,
            DataAvailability data) {
        this(repo, window, capturedAt, counts, uniqueActors, starActors, starsByMinute, data,
                List.of(), StarIndex.empty());
    }

    /** Snapshot с профилями акторов для C2, без индекса звёзд. */
    public RepoSnapshot(
            RepoRef repo,
            TimeWindow window,
            Instant capturedAt,
            EventCounts counts,
            int uniqueActors,
            int starActors,
            int[] starsByMinute,
            DataAvailability data,
            List<ActorProfile> starActorProfiles) {
        this(repo, window, capturedAt, counts, uniqueActors, starActors, starsByMinute, data,
                starActorProfiles, StarIndex.empty());
    }

    /** Snapshot с индексом звёзд для C4, без профилей акторов. */
    public RepoSnapshot(
            RepoRef repo,
            TimeWindow window,
            Instant capturedAt,
            EventCounts counts,
            int uniqueActors,
            int starActors,
            int[] starsByMinute,
            DataAvailability data,
            StarIndex starIndex) {
        this(repo, window, capturedAt, counts, uniqueActors, starActors, starsByMinute, data,
                List.of(), starIndex);
    }

    public RepoSnapshot withStarActorProfiles(List<ActorProfile> profiles) {
        return new RepoSnapshot(repo, window, capturedAt, counts, uniqueActors, starActors, starsByMinute,
                data, profiles, starIndex);
    }

    @Override
    public int[] starsByMinute() {
        return starsByMinute.clone();
    }
}
