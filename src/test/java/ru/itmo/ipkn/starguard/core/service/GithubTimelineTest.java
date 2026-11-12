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
}
