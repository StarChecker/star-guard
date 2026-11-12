package ru.itmo.ipkn.starguard.core.scoring.github;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import ru.itmo.ipkn.starguard.core.config.BurstProperties;
import ru.itmo.ipkn.starguard.core.config.GithubChecksProperties;
import ru.itmo.ipkn.starguard.core.scoring.CheckContribution;
import ru.itmo.ipkn.starguard.core.scoring.CheckIds;
import ru.itmo.ipkn.starguard.core.scoring.CheckMode;
import ru.itmo.ipkn.starguard.core.scoring.RepoScore;
import ru.itmo.ipkn.starguard.core.scoring.ScoreLevel;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static ru.itmo.ipkn.starguard.core.scoring.github.GithubSnapshots.NOW;
import static ru.itmo.ipkn.starguard.core.scoring.github.GithubSnapshots.TODAY;
import static ru.itmo.ipkn.starguard.core.scoring.github.GithubSnapshots.repo;
import static ru.itmo.ipkn.starguard.core.scoring.github.GithubSnapshots.steady;

class GithubScoringEngineTest {

    private final GithubScoringEngine engine = new GithubScoringEngine(
            GithubChecksProperties.defaults(),
            new BurstProperties(CheckMode.REAL, true, 3.0, 12.0, 10, 60, 0.3),
            new MockEnvironment()
                    .withProperty("app.scoring.github.weights.daily_burst", "0.30")
                    .withProperty("app.scoring.github.weights.dev_activity", "0.25")
                    .withProperty("app.scoring.github.weights.fork_ratio", "0.20")
                    .withProperty("app.scoring.github.weights.intraday_burst", "0.10")
                    .withProperty("app.scoring.github.weights.hourly_profile", "0.05")
                    .withProperty("app.scoring.github.weights.star_drops", "0.10")
                    .withProperty("app.scoring.github.amplification.strong-check.check-ids", "intraday_burst,star_drops")
                    .withProperty("app.scoring.github.amplification.strong-check.check-score-at-least", "0.85")
                    .withProperty("app.scoring.github.amplification.strong-check.min-confidence", "0.7")
                    .withProperty("app.scoring.github.amplification.strong-check.floor", "70"),
            Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void newRepositoryWithBoughtStarsAndForksIsHigh() {
        RepoScore score = engine.score(repo().created("2026-11-03T14:04:27Z")
                .daily(LocalDate.parse("2026-11-03"), 273, 680, 328, 536, 682, 1617, 2859, 1161, 202, 70)
                .totals(8408, 4394)
                .activity(3102, 38, 26, 1, 1).build());

        assertThat(score.level()).isIn(ScoreLevel.HIGH, ScoreLevel.CRITICAL);
        assertThat(score.rulesApplied()).contains("multi_trigger");
        assertThat(score.mocked()).isFalse();
        assertThat(score.checks()).extracting(CheckContribution::checkId).containsExactly(
                CheckIds.DAILY_BURST, CheckIds.DEV_ACTIVITY, CheckIds.FORK_RATIO,
                CheckIds.INTRADAY_BURST, CheckIds.HOURLY_PROFILE, CheckIds.STAR_DROPS);
    }

    @Test
    void newViralRepositoryWithRealDevelopmentIsLow() {
        RepoScore score = engine.score(repo().created("2026-11-05T00:00:00Z")
                .daily(LocalDate.parse("2026-11-05"), 24, 531, 1527, 1346, 2074, 1378, 1225, 136)
                .totals(8241, 729)
                .activity(729, 1522, 1209, 2034, 60).build());

        assertThat(score.level()).isEqualTo(ScoreLevel.LOW);
        assertThat(score.score()).isZero();
    }

    @Test
    void popularMatureRepositoryWithSteadyStarsIsLow() {
        RepoScore score = engine.score(repo().daily(TODAY.minusDays(119), steady(120, 600))
                .activity(1000, 0, 0, 0, 0).build());

        assertThat(score.level()).isEqualTo(ScoreLevel.LOW);
        assertThat(score.score()).isZero();
        assertThat(score.insufficientData()).isFalse();
    }

    @Test
    void untrackedRepositoryKeepsIntradayChecksOutOfTheScore() {
        RepoScore score = engine.score(repo().build());

        assertThat(score.checks())
                .filteredOn(check -> check.checkId().equals(CheckIds.INTRADAY_BURST))
                .allSatisfy(check -> assertThat(check.active()).isFalse());
    }
}
