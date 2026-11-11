package ru.itmo.ipkn.starguard.core.index;

import ru.itmo.ipkn.starguard.core.domain.ActorActivity;
import ru.itmo.ipkn.starguard.core.domain.EventCounts;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Компактная агрегация одного репозитория за один час:
 * счётчики, минутный ряд звёзд и число уникальных акторов.
 * Неизменяема и разделяется между снимками индекса (см. {@link HourDigest}).
 */
public record RepoHourAggregate(
        Instant hour,
        EventCounts counts,
        int[] starsByMinute,
        int uniqueActors,
        int starActors,
        List<ActorActivity> actors
) {

    /**
     * Общий нулевой ряд: у большинства репозиториев за час нет ни одной звезды,
     * а индекс хранит агрегаты всех репозиториев окна. Наружу массив не отдаётся —
     * аксессор возвращает копию.
     */
    private static final int[] NO_STARS = new int[60];

    public RepoHourAggregate {
        Objects.requireNonNull(hour, "hour");
        Objects.requireNonNull(counts, "counts");
        if (starsByMinute == null || starsByMinute.length != 60) {
            throw new IllegalArgumentException("starsByMinute must have exactly 60 entries");
        }
        starsByMinute = isZero(starsByMinute) ? NO_STARS : starsByMinute.clone();
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
        return new RepoHourAggregate(hour, EventCounts.empty(), NO_STARS, 0, 0);
    }

    private static boolean isZero(int[] values) {
        for (int value : values) {
            if (value != 0) {
                return false;
            }
        }
        return true;
    }
}
