package ru.itmo.ipkn.starguard.core.scoring;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import ru.itmo.ipkn.starguard.core.config.EngagementProperties;
import ru.itmo.ipkn.starguard.core.domain.DataAvailability;
import ru.itmo.ipkn.starguard.core.domain.EventCounts;
import ru.itmo.ipkn.starguard.core.domain.RepoRef;
import ru.itmo.ipkn.starguard.core.domain.RepoSnapshot;
import ru.itmo.ipkn.starguard.core.domain.TimeWindow;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

class EngagementCheckTest {

    private static final TimeWindow WINDOW = new TimeWindow(
            Instant.parse("2026-11-10T00:00:00Z"), Instant.parse("2026-11-10T03:00:00Z"), 3);

    private final EngagementCheck check = new EngagementCheck(EngagementProperties.defaults());

    @Test
    void starStuffingScoresHigh() {
        // 120 звёзд и 2 пуша: engagement = 2/120 ≈ 0.017 → s3 ≈ 0.917, gate = 1
        CheckResult result = check.evaluate(snapshot(120, 0, 2, 0, 0));

        assertThat(result.checkId()).isEqualTo(CheckIds.ENGAGEMENT);
        assertThat(result.score()).isGreaterThanOrEqualTo(0.9);
        assertThat(result.confidence()).isEqualTo(0.9);
        assertThat(result.details())
                .containsEntry("mocked", false)
                .containsEntry("stars", 120)
                .containsEntry("activity", 2)
                .containsEntry("engagement", 0.017)
                .doesNotContainKey("reason");
    }

    @Test
    void healthyLibraryScoresLow() {
        // 30 звёзд, 4 форка, 40 пушей, 10 issues, 12 PR: engagement = 66/30 = 2.2 → s3 = 0
        CheckResult result = check.evaluate(snapshot(30, 4, 40, 10, 12));

        assertThat(result.score()).isLessThanOrEqualTo(0.2);
        assertThat(result.confidence()).isEqualTo(0.9);
        assertThat(result.details()).containsEntry("engagement", 2.2);
    }

    @Test
    void engagementAtThresholdGivesZero() {
        // 50 звёзд, 10 событий активности: engagement = 0.2 ровно на пороге
        CheckResult result = check.evaluate(snapshot(50, 0, 10, 0, 0));

        assertThat(result.score()).isZero();
    }

    @Test
    void gateScalesScoreWhileStarsBelowGate() {
        // 10 звёзд без активности: s3 = 1, gate = 10/20 = 0.5
        CheckResult result = check.evaluate(snapshot(10, 0, 0, 0, 0));

        assertThat(result.score()).isCloseTo(0.5, within(1e-9));
        assertThat(result.details()).containsEntry("gate", 0.5);
    }

    @Test
    void gateSaturatesAtStarsGate() {
        assertThat(check.evaluate(snapshot(20, 0, 0, 0, 0)).score()).isEqualTo(1.0);
        assertThat(check.evaluate(snapshot(200, 0, 0, 0, 0)).score()).isEqualTo(1.0);
    }

    @Test
    void tooFewStarsMakesCheckInactive() {
        CheckResult result = check.evaluate(snapshot(4, 0, 0, 0, 0));

        assertThat(result.confidence()).isLessThan(0.5);
        assertThat(result.details())
                .containsEntry("reason", "too_few_stars")
                .containsEntry("min_stars_for_confidence", 5);
    }

    @Test
    void noStarsDoesNotDivideByZero() {
        CheckResult result = check.evaluate(snapshot(0, 3, 10, 2, 1));

        assertThat(result.score()).isZero();
        assertThat(result.confidence()).isZero();
        assertThat(result.details()).containsEntry("engagement", 16.0);
    }

    @Test
    void usesThresholdsFromProperties() {
        EngagementCheck custom = new EngagementCheck(
                new EngagementProperties(CheckMode.REAL, 0.5, 50, 1, 0.8));

        // 100 звёзд, 25 событий: engagement = 0.25 → s3 = (0.5 − 0.25)/0.5 = 0.5, gate = 1
        CheckResult result = custom.evaluate(snapshot(100, 0, 25, 0, 0));

        assertThat(result.score()).isCloseTo(0.5, within(1e-9));
        assertThat(result.confidence()).isEqualTo(0.8);
    }

    @Test
    void rejectsInvalidProperties() {
        assertThatThrownBy(() -> new EngagementProperties(null, 0.0, null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new EngagementProperties(null, null, 0, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new EngagementProperties(null, null, null, -1, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new EngagementProperties(null, null, null, null, 1.5))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void typoInModeFailsStartupInsteadOfDroppingCheck() {
        new ApplicationContextRunner()
                .withUserConfiguration(PropertiesConfig.class)
                .withPropertyValues(EngagementProperties.MODE_PROPERTY + "=reall")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void modeBindsCaseInsensitively() {
        new ApplicationContextRunner()
                .withUserConfiguration(PropertiesConfig.class)
                .withPropertyValues(EngagementProperties.MODE_PROPERTY + "=real")
                .run(context -> assertThat(context.getBean(EngagementProperties.class).mode())
                        .isEqualTo(CheckMode.REAL));
    }

    private static RepoSnapshot snapshot(int stars, int forks, int pushes, int issues, int pullRequests) {
        int total = stars + forks + pushes + issues + pullRequests;
        EventCounts counts = new EventCounts(total, stars, forks, pushes, issues, pullRequests, 0);
        return new RepoSnapshot(new RepoRef("demo", "repo"), WINDOW, WINDOW.to(),
                counts, total, stars, null, DataAvailability.pocDefaults());
    }

    @EnableConfigurationProperties(EngagementProperties.class)
    static class PropertiesConfig {
    }
}
