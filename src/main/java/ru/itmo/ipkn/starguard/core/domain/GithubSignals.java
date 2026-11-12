package ru.itmo.ipkn.starguard.core.domain;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

public record GithubSignals(
        String nodeId,
        Instant repoCreatedAt,
        long totalStars,
        long totalForks,
        LocalDate firstDay,
        int[] dailyStars,
        WindowActivity activity,
        Intraday intraday
) {

    public GithubSignals {
        Objects.requireNonNull(repoCreatedAt, "repoCreatedAt");
        Objects.requireNonNull(firstDay, "firstDay");
        Objects.requireNonNull(activity, "activity");
        dailyStars = dailyStars == null ? new int[0] : dailyStars.clone();
    }

    @Override
    public int[] dailyStars() {
        return dailyStars.clone();
    }

    public int days() {
        return dailyStars.length;
    }

    public LocalDate day(int index) {
        return firstDay.plusDays(index);
    }

    public long ageDays(Instant now) {
        return Math.max(1, Duration.between(repoCreatedAt, now).toDays());
    }

    public GithubSignals withIntraday(Intraday value) {
        return new GithubSignals(nodeId, repoCreatedAt, totalStars, totalForks, firstDay, dailyStars, activity, value);
    }

    public record WindowActivity(
            Instant from,
            Instant to,
            int stars,
            int forks,
            boolean forksTruncated,
            int issues,
            int pullRequests,
            int commits,
            int contributors
    ) {

        public WindowActivity {
            Objects.requireNonNull(from, "from");
            Objects.requireNonNull(to, "to");
        }

        public long days() {
            return Math.max(1, Duration.between(from, to).toDays());
        }

        public int development() {
            return issues + pullRequests + commits;
        }
    }

    public record Intraday(
            Instant from,
            int stepMinutes,
            int[] stars,
            Instant hourlyFrom,
            int[] hourlyStars,
            double[] globalHourShare,
            Instant trackedSince
    ) {

        public static final int MISSING = -1;

        public Intraday {
            Objects.requireNonNull(from, "from");
            Objects.requireNonNull(hourlyFrom, "hourlyFrom");
            Objects.requireNonNull(trackedSince, "trackedSince");
            if (stepMinutes < 1) {
                throw new IllegalArgumentException("stepMinutes must be positive");
            }
            stars = stars == null ? new int[0] : stars.clone();
            hourlyStars = hourlyStars == null ? new int[0] : hourlyStars.clone();
            globalHourShare = globalHourShare == null ? new double[0] : globalHourShare.clone();
        }

        @Override
        public int[] stars() {
            return stars.clone();
        }

        @Override
        public int[] hourlyStars() {
            return hourlyStars.clone();
        }

        @Override
        public double[] globalHourShare() {
            return globalHourShare.clone();
        }
    }
}
