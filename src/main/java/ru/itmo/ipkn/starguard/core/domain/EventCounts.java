package ru.itmo.ipkn.starguard.core.domain;

/** Счётчики событий репозитория за окно. */
public record EventCounts(
        int total,
        int stars,
        int forks,
        int pushes,
        int issues,
        int pullRequests,
        int other
) {

    public static EventCounts empty() {
        return new EventCounts(0, 0, 0, 0, 0, 0, 0);
    }
}
