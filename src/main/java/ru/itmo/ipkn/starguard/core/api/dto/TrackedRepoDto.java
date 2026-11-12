package ru.itmo.ipkn.starguard.core.api.dto;

import java.time.Instant;
import java.util.List;

public record TrackedRepoDto(
        String fullName,
        Integer popularRank,
        Instant watchedUntil,
        Instant trackedSince,
        Instant lastPolledAt,
        int stars,
        int forks,
        List<Point> last24h,
        List<Point> hourly
) {

    public record Point(Instant at, int stars, int forks) {
    }
}
