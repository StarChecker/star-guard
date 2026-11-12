package ru.itmo.ipkn.starguard.core.scoring.github;

import ru.itmo.ipkn.starguard.core.domain.DataAvailability;
import ru.itmo.ipkn.starguard.core.domain.EventCounts;
import ru.itmo.ipkn.starguard.core.domain.GithubSignals;
import ru.itmo.ipkn.starguard.core.domain.RepoRef;
import ru.itmo.ipkn.starguard.core.domain.RepoSnapshot;
import ru.itmo.ipkn.starguard.core.domain.StarIndex;
import ru.itmo.ipkn.starguard.core.domain.TimeWindow;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;

final class GithubSnapshots {

    static final Instant NOW = Instant.parse("2026-11-12T10:30:00Z");
    static final LocalDate TODAY = LocalDate.parse("2026-11-12");
    static final Instant WINDOW_FROM = TODAY.minusDays(29).atStartOfDay(ZoneOffset.UTC).toInstant();
    static final Instant WINDOW_TO = Instant.parse("2026-11-12T11:00:00Z");

    private GithubSnapshots() {
    }

    static Builder repo() {
        return new Builder();
    }

    static int[] steady(int days, int perDay) {
        int[] daily = new int[days];
        Arrays.fill(daily, perDay);
        return daily;
    }

    static final class Builder {

        private Instant createdAt = Instant.parse("2015-01-01T00:00:00Z");
        private LocalDate firstDay = TODAY.minusDays(119);
        private int[] daily = steady(120, 50);
        private long totalStars = 100_000;
        private long totalForks = 10_000;
        private int forks = 150;
        private int issues = 120;
        private int pulls = 160;
        private int commits = 300;
        private int contributors = 40;
        private GithubSignals.Intraday intraday;

        Builder created(String at) {
            this.createdAt = Instant.parse(at);
            return this;
        }

        Builder daily(LocalDate first, int... stars) {
            this.firstDay = first;
            this.daily = stars;
            return this;
        }

        Builder totals(long stars, long forkTotal) {
            this.totalStars = stars;
            this.totalForks = forkTotal;
            return this;
        }

        Builder activity(int forkCount, int issueCount, int pullCount, int commitCount, int authors) {
            this.forks = forkCount;
            this.issues = issueCount;
            this.pulls = pullCount;
            this.commits = commitCount;
            this.contributors = authors;
            return this;
        }

        Builder intraday(GithubSignals.Intraday value) {
            this.intraday = value;
            return this;
        }

        RepoSnapshot build() {
            int windowStars = 0;
            for (int i = 0; i < daily.length; i++) {
                if (!firstDay.plusDays(i).isBefore(TODAY.minusDays(29))) {
                    windowStars += daily[i];
                }
            }
            GithubSignals signals = new GithubSignals("R_1", createdAt, totalStars, totalForks, firstDay, daily,
                    new GithubSignals.WindowActivity(WINDOW_FROM, WINDOW_TO, windowStars, forks, false, issues,
                            pulls, commits, contributors),
                    intraday);
            TimeWindow window = new TimeWindow(WINDOW_FROM, WINDOW_TO,
                    (int) Duration.between(WINDOW_FROM, WINDOW_TO).toHours());
            EventCounts counts = new EventCounts(windowStars + forks + issues + pulls + commits,
                    windowStars, forks, commits, issues, pulls, 0);
            return new RepoSnapshot(new RepoRef("acme", "tool"), window, NOW, counts, contributors, 0, null,
                    DataAvailability.pocDefaults(), List.of(), StarIndex.empty(), signals);
        }
    }

    static GithubSignals.Intraday intraday(int[] fiveMinuteStars, int[] hourlyStars, double[] global) {
        Instant rawFrom = NOW.minus(Duration.ofDays(1)).minus(Duration.ofMinutes(5));
        return new GithubSignals.Intraday(rawFrom, 5, fiveMinuteStars,
                NOW.minus(Duration.ofDays(7)).minus(Duration.ofMinutes(30)), hourlyStars, global,
                NOW.minus(Duration.ofDays(10)));
    }

    static double[] uniformHours() {
        double[] share = new double[24];
        Arrays.fill(share, 1.0 / 24);
        return share;
    }
}
