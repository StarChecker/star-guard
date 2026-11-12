package ru.itmo.ipkn.starguard.core.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import java.time.Duration;

@ConfigurationProperties("app.account-pool")
public record AccountPoolProperties(Boolean enabled, Integer maxAccounts, Integer refreshBatch,
        Integer discoveryBatch, Integer minStars, Integer maxRepos, Integer maxFollowers,
        Integer maxContributions, Integer burstSize, Double burstShare, Integer minMatches,
        Integer fullSignalMatches, Integer maxScoreFloor, Integer rateReserve,
        Duration interval, Duration refreshAfter, Duration staleAfter) {
    public AccountPoolProperties {
        enabled = Boolean.TRUE.equals(enabled);
        maxAccounts = maxAccounts == null ? 10000 : maxAccounts;
        refreshBatch = refreshBatch == null ? 100 : refreshBatch;
        discoveryBatch = discoveryBatch == null ? 20 : discoveryBatch;
        minStars = minStars == null ? 1000 : minStars;
        maxRepos = maxRepos == null ? 1 : maxRepos;
        maxFollowers = maxFollowers == null ? 5 : maxFollowers;
        maxContributions = maxContributions == null ? 2 : maxContributions;
        burstSize = burstSize == null ? 10 : burstSize;
        burstShare = burstShare == null ? 0.5 : burstShare;
        minMatches = minMatches == null ? 5 : minMatches;
        fullSignalMatches = fullSignalMatches == null ? 100 : fullSignalMatches;
        maxScoreFloor = maxScoreFloor == null ? 70 : maxScoreFloor;
        rateReserve = rateReserve == null ? 1000 : rateReserve;
        interval = interval == null ? Duration.ofMinutes(10) : interval;
        refreshAfter = refreshAfter == null ? Duration.ofDays(1) : refreshAfter;
        staleAfter = staleAfter == null ? Duration.ofDays(3) : staleAfter;
        if (maxAccounts < 1 || refreshBatch < 1 || discoveryBatch < 1 || discoveryBatch > 100
                || minStars < 1 || maxRepos < 0 || maxFollowers < 0 || maxContributions < 0
                || burstSize < 2 || burstSize > 100 || !Double.isFinite(burstShare) || burstShare <= 0 || burstShare > 1
                || minMatches < 1 || fullSignalMatches < minMatches || maxScoreFloor < 1 || maxScoreFloor > 100
                || rateReserve < 0 || interval.isNegative() || interval.isZero()
                || refreshAfter.isNegative() || refreshAfter.isZero() || staleAfter.compareTo(refreshAfter) < 0)
            throw new IllegalArgumentException("Invalid app.account-pool settings");
    }
}
