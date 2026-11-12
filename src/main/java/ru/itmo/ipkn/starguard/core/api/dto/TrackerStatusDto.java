package ru.itmo.ipkn.starguard.core.api.dto;

import java.time.Duration;
import java.time.Instant;

public record TrackerStatusDto(
        boolean active,
        int popularRepos,
        int watchedRepos,
        long pollIntervalSeconds,
        Instant lastPollAt,
        Integer lastPollRepos,
        Integer lastPollFailed,
        Integer lastPollDurationMs,
        Integer rateLimitRemaining
) {

    public static long seconds(Duration duration) {
        return duration.toSeconds();
    }
}
