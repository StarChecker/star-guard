package ru.itmo.ipkn.starguard.core.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.scoring.github.checks")
public record GithubChecksProperties(
        DailyBurst dailyBurst,
        DevActivity devActivity,
        ForkRatio forkRatio,
        IntradayBurst intradayBurst,
        HourlyProfile hourlyProfile,
        StarDrops starDrops
) {

    public GithubChecksProperties {
        dailyBurst = dailyBurst == null ? new DailyBurst(null, null, null, null, null, null, null) : dailyBurst;
        devActivity = devActivity == null ? new DevActivity(null, null, null, null, null, null) : devActivity;
        forkRatio = forkRatio == null ? new ForkRatio(null, null, null, null, null) : forkRatio;
        intradayBurst = intradayBurst == null ? new IntradayBurst(null, null) : intradayBurst;
        hourlyProfile = hourlyProfile == null ? new HourlyProfile(null, null, null, null, null) : hourlyProfile;
        starDrops = starDrops == null ? new StarDrops(null, null, null, null, null) : starDrops;
    }

    public static GithubChecksProperties defaults() {
        return new GithubChecksProperties(null, null, null, null, null, null);
    }

    public record DailyBurst(
            Integer baselineDays,
            Integer minBaselineDays,
            Double ratioFloor,
            Double ratioSpan,
            Integer minExcess,
            Double confidence,
            Integer topDays
    ) {
        public DailyBurst {
            baselineDays = positive(baselineDays, 28, "daily-burst.baseline-days");
            minBaselineDays = positive(minBaselineDays, 7, "daily-burst.min-baseline-days");
            ratioFloor = nonNegative(ratioFloor, 3.0, "daily-burst.ratio-floor");
            ratioSpan = positive(ratioSpan, 12.0, "daily-burst.ratio-span");
            minExcess = positive(minExcess, 100, "daily-burst.min-excess");
            confidence = share(confidence, 0.9, "daily-burst.confidence");
            topDays = positive(topDays, 5, "daily-burst.top-days");
            if (minBaselineDays > baselineDays) {
                throw new IllegalArgumentException("daily-burst.min-baseline-days must not exceed baseline-days");
            }
        }
    }

    public record DevActivity(
            Double floor,
            Integer minStars,
            Integer maxAgeDays,
            Integer baselineDays,
            Double unexplainedShare,
            Double confidence
    ) {
        public DevActivity {
            floor = positive(floor, 0.05, "dev-activity.floor");
            minStars = positive(minStars, 100, "dev-activity.min-stars");
            maxAgeDays = positive(maxAgeDays, 90, "dev-activity.max-age-days");
            baselineDays = positive(baselineDays, 60, "dev-activity.baseline-days");
            unexplainedShare = share(unexplainedShare, 0.5, "dev-activity.unexplained-share");
            confidence = share(confidence, 0.9, "dev-activity.confidence");
        }
    }

    public record ForkRatio(Double floor, Double span, Integer minStars, Integer maxAgeDays, Double confidence) {
        public ForkRatio {
            floor = nonNegative(floor, 0.2, "fork-ratio.floor");
            span = positive(span, 0.2, "fork-ratio.span");
            minStars = positive(minStars, 200, "fork-ratio.min-stars");
            maxAgeDays = positive(maxAgeDays, 90, "fork-ratio.max-age-days");
            confidence = share(confidence, 0.9, "fork-ratio.confidence");
        }
    }

    public record IntradayBurst(Double minCoverage, Double confidence) {
        public IntradayBurst {
            minCoverage = share(minCoverage, 0.5, "intraday-burst.min-coverage");
            confidence = share(confidence, 0.9, "intraday-burst.confidence");
        }
    }

    public record HourlyProfile(Integer minDays, Double klFloor, Double klSpan, Integer minStars, Double confidence) {
        public HourlyProfile {
            minDays = positive(minDays, 3, "hourly-profile.min-days");
            klFloor = nonNegative(klFloor, 0.3, "hourly-profile.kl-floor");
            klSpan = positive(klSpan, 1.2, "hourly-profile.kl-span");
            minStars = positive(minStars, 50, "hourly-profile.min-stars");
            confidence = share(confidence, 0.9, "hourly-profile.confidence");
        }
    }

    public record StarDrops(Double shareFloor, Double shareSpan, Integer minDropped, Integer minHours, Double confidence) {
        public StarDrops {
            shareFloor = share(shareFloor, 0.03, "star-drops.share-floor");
            shareSpan = positive(shareSpan, 0.12, "star-drops.share-span");
            minDropped = positive(minDropped, 30, "star-drops.min-dropped");
            minHours = positive(minHours, 12, "star-drops.min-hours");
            confidence = share(confidence, 0.9, "star-drops.confidence");
        }
    }

    private static int positive(Integer value, int fallback, String name) {
        int result = value == null ? fallback : value;
        if (result < 1) {
            throw new IllegalArgumentException("app.scoring.github.checks." + name + " must be >= 1");
        }
        return result;
    }

    private static double positive(Double value, double fallback, String name) {
        double result = value == null ? fallback : value;
        if (!Double.isFinite(result) || result <= 0) {
            throw new IllegalArgumentException("app.scoring.github.checks." + name + " must be > 0");
        }
        return result;
    }

    private static double nonNegative(Double value, double fallback, String name) {
        double result = value == null ? fallback : value;
        if (!Double.isFinite(result) || result < 0) {
            throw new IllegalArgumentException("app.scoring.github.checks." + name + " must be >= 0");
        }
        return result;
    }

    private static double share(Double value, double fallback, String name) {
        double result = value == null ? fallback : value;
        if (!Double.isFinite(result) || result < 0 || result > 1) {
            throw new IllegalArgumentException("app.scoring.github.checks." + name + " must be within [0, 1]");
        }
        return result;
    }
}
