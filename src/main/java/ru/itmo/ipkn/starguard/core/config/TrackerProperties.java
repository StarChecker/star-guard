package ru.itmo.ipkn.starguard.core.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "app.tracker")
public record TrackerProperties(
        Boolean enabled,
        String jdbcUrl,
        String username,
        String password,
        Integer poolSize,
        Duration pollInterval,
        Integer popularSize,
        String popularRefreshCron,
        Duration watchTtl,
        Integer maxWatched,
        Duration rawRetention,
        Duration hourlyRetention,
        Integer rateLimitReserve,
        Duration searchPageDelay,
        Integer batchSize,
        Integer profileDays,
        Rising rising
) {

    public record Rising(
            Boolean enabled,
            Integer maxAgeDays,
            Integer minStars,
            Integer dayStars,
            Integer exitDayStars,
            Integer exitDays,
            Integer max,
            String discoveryCron
    ) {
        public Rising {
            enabled = enabled == null || enabled;
            maxAgeDays = maxAgeDays == null ? 30 : maxAgeDays;
            minStars = minStars == null ? 100 : minStars;
            dayStars = dayStars == null ? 100 : dayStars;
            exitDayStars = exitDayStars == null ? 20 : exitDayStars;
            exitDays = exitDays == null ? 3 : exitDays;
            max = max == null ? 3000 : max;
            discoveryCron = discoveryCron == null || discoveryCron.isBlank() ? "0 20 * * * *" : discoveryCron;
            if (maxAgeDays < 1 || minStars < 1 || dayStars < 1 || exitDayStars < 0 || exitDays < 1 || max < 0) {
                throw new IllegalArgumentException("Invalid app.tracker.rising configuration");
            }
        }
    }

    public static final String ENABLED_PROPERTY = "app.tracker.enabled";

    public TrackerProperties {
        enabled = enabled != null && enabled;
        jdbcUrl = jdbcUrl == null || jdbcUrl.isBlank() ? "jdbc:postgresql://localhost:5432/starguard" : jdbcUrl;
        username = username == null ? "starguard" : username;
        password = password == null ? "starguard" : password;
        poolSize = poolSize == null ? 4 : poolSize;
        pollInterval = pollInterval == null ? Duration.ofMinutes(5) : pollInterval;
        popularSize = popularSize == null ? 10_000 : popularSize;
        popularRefreshCron = popularRefreshCron == null || popularRefreshCron.isBlank()
                ? "0 30 3 * * *" : popularRefreshCron;
        watchTtl = watchTtl == null ? Duration.ofDays(30) : watchTtl;
        maxWatched = maxWatched == null ? 5_000 : maxWatched;
        rawRetention = rawRetention == null ? Duration.ofHours(26) : rawRetention;
        hourlyRetention = hourlyRetention == null ? Duration.ofDays(8) : hourlyRetention;
        rateLimitReserve = rateLimitReserve == null ? 500 : rateLimitReserve;
        searchPageDelay = searchPageDelay == null ? Duration.ofMillis(250) : searchPageDelay;
        batchSize = batchSize == null ? 100 : batchSize;
        profileDays = profileDays == null ? 7 : profileDays;
        rising = rising == null ? new Rising(null, null, null, null, null, null, null, null) : rising;
        if (poolSize < 1) {
            throw new IllegalArgumentException("app.tracker.pool-size must be >= 1");
        }
        if (pollInterval.compareTo(Duration.ofMinutes(1)) < 0) {
            throw new IllegalArgumentException("app.tracker.poll-interval must be at least 1m");
        }
        if (popularSize < 0 || popularSize > 100_000) {
            throw new IllegalArgumentException("app.tracker.popular-size must be within [0, 100000]");
        }
        if (maxWatched < 0) {
            throw new IllegalArgumentException("app.tracker.max-watched must be >= 0");
        }
        if (batchSize < 1 || batchSize > 100) {
            throw new IllegalArgumentException("app.tracker.batch-size must be within [1, 100]");
        }
        if (rawRetention.compareTo(Duration.ofHours(25)) < 0) {
            throw new IllegalArgumentException("app.tracker.raw-retention must cover at least 25h");
        }
        if (hourlyRetention.compareTo(Duration.ofDays(profileDays).plusHours(1)) < 0) {
            throw new IllegalArgumentException("app.tracker.hourly-retention must cover profile-days");
        }
        if (watchTtl.isNegative() || searchPageDelay.isNegative() || rateLimitReserve < 0 || profileDays < 1) {
            throw new IllegalArgumentException("Invalid app.tracker configuration");
        }
    }

    public static TrackerProperties defaults() {
        return new TrackerProperties(null, null, null, null, null, null, null, null, null, null, null, null, null,
                null, null, null, null);
    }
}
