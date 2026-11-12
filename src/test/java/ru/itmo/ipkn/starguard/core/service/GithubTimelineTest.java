package ru.itmo.ipkn.starguard.core.service;

import org.junit.jupiter.api.Test;
import ru.itmo.ipkn.starguard.core.domain.GithubSignals;
import ru.itmo.ipkn.starguard.core.domain.StarTimeline;
import ru.itmo.ipkn.starguard.core.scoring.CheckContribution;
import ru.itmo.ipkn.starguard.core.scoring.CheckIds;
import ru.itmo.ipkn.starguard.core.scoring.RepoScore;
import ru.itmo.ipkn.starguard.core.scoring.ScoreLevel;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class GithubTimelineTest {

    @Test
    void buildsDailyPointsOfWindowAndMarksExcessOfBurstDays() {
        GithubSignals github = new GithubSignals("R_1", Instant.parse("2020-01-01T00:00:00Z"), 5000, 300,
                LocalDate.parse("2026-11-01"), new int[]{40, 40, 40, 1240, 40},
                new GithubSignals.WindowActivity(Instant.parse("2026-11-02T00:00:00Z"),
                        Instant.parse("2026-11-06T00:00:00Z"), 1360, 10, false, 5, 5, 5, 3),
                null);
        CheckContribution burst = new CheckContribution(CheckIds.DAILY_BURST, 1.0, 0.9, 0.3, 30, true, null,
                Map.of("burst_days", List.of(Map.of("day", "2026-11-04", "stars", 1240, "excess", 1200))));
        RepoScore score = new RepoScore(30, ScoreLevel.MEDIUM, 0.9, false, List.of(burst), false, List.of(),
                Instant.parse("2026-11-06T00:00:00Z"));

        StarTimeline timeline = StarTimelineService.daily(github, score);

        assertThat(timeline.bucketMinutes()).isEqualTo(1440);
        assertThat(timeline.points()).hasSize(4);
        assertThat(timeline.points().getFirst().at()).isEqualTo(Instant.parse("2026-11-02T00:00:00Z"));
        assertThat(timeline.points()).extracting(StarTimeline.Point::suspicious).containsExactly(0, 0, 1200, 0);
        assertThat(timeline.suspiciousStars()).isEqualTo(1200);
        assertThat(timeline.suspiciousByCheck()).containsEntry(CheckIds.DAILY_BURST, 1200);
    }

    @Test
    void bucketsLongPeriodsIntoWeeksAndMonths() {
        LocalDate first = LocalDate.parse("2025-01-06");   // понедельник
        int[] daily = new int[200];
        java.util.Arrays.fill(daily, 10);
        GithubSignals github = new GithubSignals("R_1", Instant.parse("2015-01-01T00:00:00Z"), 2000, 100,
                first, daily,
                new GithubSignals.WindowActivity(first.atStartOfDay(java.time.ZoneOffset.UTC).toInstant(),
                        first.plusDays(200).atStartOfDay(java.time.ZoneOffset.UTC).toInstant(),
                        2000, 0, false, 0, 0, 0, 0),
                null);
        RepoScore score = new RepoScore(0, ScoreLevel.LOW, 0.0, false, List.of(), false, List.of(),
                Instant.parse("2026-11-06T00:00:00Z"));

        StarTimeline weekly = StarTimelineService.daily(github, score);

        assertThat(weekly.bucketMinutes()).isEqualTo(7 * 24 * 60);
        assertThat(weekly.points()).hasSize(29);
        assertThat(weekly.points().getFirst().at()).isEqualTo(
                first.atStartOfDay(java.time.ZoneOffset.UTC).toInstant());
        assertThat(weekly.points().getFirst().stars()).isEqualTo(70);
        assertThat(weekly.points().getLast().stars()).isEqualTo(2000 - 28 * 70);

        int[] year = new int[365];
        java.util.Arrays.fill(year, 1);
        GithubSignals longGithub = new GithubSignals("R_1", Instant.parse("2010-01-01T00:00:00Z"), 1000, 10,
                first, year,
                new GithubSignals.WindowActivity(first.atStartOfDay(java.time.ZoneOffset.UTC).toInstant(),
                        first.plusDays(365).atStartOfDay(java.time.ZoneOffset.UTC).toInstant(),
                        365, 0, false, 0, 0, 0, 0),
                null);
        StarTimeline yearly = StarTimelineService.daily(longGithub, score);

        assertThat(yearly.bucketMinutes()).isEqualTo(7 * 24 * 60);
        assertThat(yearly.points()).hasSize(53);

        int[] multiYear = new int[800];
        java.util.Arrays.fill(multiYear, 1);
        GithubSignals huge = new GithubSignals("R_1", Instant.parse("2010-01-01T00:00:00Z"), 5000, 10,
                first, multiYear,
                new GithubSignals.WindowActivity(first.atStartOfDay(java.time.ZoneOffset.UTC).toInstant(),
                        first.plusDays(800).atStartOfDay(java.time.ZoneOffset.UTC).toInstant(),
                        800, 0, false, 0, 0, 0, 0),
                null);
        StarTimeline monthly = StarTimelineService.daily(huge, score);

        assertThat(monthly.bucketMinutes()).isEqualTo(30 * 24 * 60);
        assertThat(monthly.points().getFirst().at()).isEqualTo(
                LocalDate.parse("2025-01-01").atStartOfDay(java.time.ZoneOffset.UTC).toInstant());
    }
}
