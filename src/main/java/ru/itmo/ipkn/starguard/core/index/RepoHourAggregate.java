package ru.itmo.ipkn.starguard.core.index;

import ru.itmo.ipkn.starguard.core.domain.ActorActivity;
import ru.itmo.ipkn.starguard.core.domain.EventCounts;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Компактная агрегация одного репозитория за один час:
 * счётчики, минутный ряд звёзд и число уникальных акторов.
 */
public record RepoHourAggregate(
        Instant hour,
        EventCounts counts,
        int[] starsByMinute,
        int uniqueActors,
        int starActors,
        List<ActorActivity> actors
) {

    public RepoHourAggregate {
        Objects.requireNonNull(hour, "hour");
        Objects.requireNonNull(counts, "counts");
        if (starsByMinute == null || starsByMinute.length != 60) {
            throw new IllegalArgumentException("starsByMinute must have exactly 60 entries");
        }
        starsByMinute = starsByMinute.clone();
        actors = actors == null ? List.of() : List.copyOf(actors);
    }

    public RepoHourAggregate(
            Instant hour, EventCounts counts, int[] starsByMinute, int uniqueActors, int starActors) {
        this(hour, counts, starsByMinute, uniqueActors, starActors, List.of());
    }

    @Override
    public int[] starsByMinute() {
        return starsByMinute.clone();
    }

    public static RepoHourAggregate empty(Instant hour) {
        return new RepoHourAggregate(hour, EventCounts.empty(), new int[60], 0, 0);
    }
}
