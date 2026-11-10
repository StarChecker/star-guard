package ru.itmo.ipkn.starguard.core.scoring;

import org.junit.jupiter.api.Test;
import ru.itmo.ipkn.starguard.core.config.ScoringProperties;
import ru.itmo.ipkn.starguard.core.domain.DataAvailability;
import ru.itmo.ipkn.starguard.core.domain.EventCounts;
import ru.itmo.ipkn.starguard.core.domain.RepoRef;
import ru.itmo.ipkn.starguard.core.domain.RepoSnapshot;
import ru.itmo.ipkn.starguard.core.domain.TimeWindow;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

class ScoringEngineTest {

    private static final Map<String, Double> WEIGHTS = Map.of(
            CheckIds.BURST, 0.30,
            CheckIds.FRESH_ACCOUNTS, 0.22,
            CheckIds.ENGAGEMENT, 0.18,
            CheckIds.ACTOR_OVERLAP, 0.22,
            CheckIds.CIRCADIAN, 0.08);
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-10T06:00:00Z"), ZoneOffset.UTC);
    private static final TimeWindow WINDOW = new TimeWindow(
            Instant.parse("2026-11-10T00:00:00Z"), Instant.parse("2026-11-10T03:00:00Z"), 3);
    private static final RepoSnapshot SNAPSHOT = new RepoSnapshot(
            new RepoRef("demo", "repo"), WINDOW, CLOCK.instant(),
            EventCounts.empty(), 0, 0, new int[180], DataAvailability.pocDefaults());

    @Test
    void buildsWeightedScoreWithoutAmplification() {
        DefaultScoringEngine engine = new DefaultScoringEngine(List.of(
                stub(CheckIds.BURST, 0.10, 0.9),
                stub(CheckIds.FRESH_ACCOUNTS, 0.10, 0.9),
                stub(CheckIds.ENGAGEMENT, 0.15, 0.9),
                stub(CheckIds.ACTOR_OVERLAP, 0.05, 0.9),
                stub(CheckIds.CIRCADIAN, 0.10, 0.9)), properties(0.5), CLOCK);

        RepoScore score = engine.score(SNAPSHOT);

        assertThat(score.score()).isEqualTo(10);
        assertThat(score.level()).isEqualTo(ScoreLevel.LOW);
        assertThat(score.mocked()).isFalse();
        assertThat(score.rulesApplied()).isEmpty();
        assertThat(score.checks()).hasSize(5);
        assertThat(score.checks().getFirst().checkId()).isEqualTo(CheckIds.BURST);
        assertThat(score.checks().getFirst().contribution()).isEqualTo(3.0);
    }

    @Test
    void suspiciousMockProfileTriggersBothAmplifications() {
        ScoringProperties properties = properties(0.5);
        MockModeContext context = new MockModeContext();
        DefaultScoringEngine engine = new DefaultScoringEngine(List.of(
                new MockBurstCheck(properties, context),
                new MockFreshAccountsCheck(properties, context),
                new MockEngagementCheck(properties, context),
                new MockActorOverlapCheck(properties, context),
                new MockCircadianCheck(properties, context)), properties, CLOCK);

        RepoScore score = context.callWith(MockMode.SUSPICIOUS, () -> engine.score(SNAPSHOT));

        assertThat(score.score()).isEqualTo(86);
        assertThat(score.level()).isEqualTo(ScoreLevel.CRITICAL);
        assertThat(score.mocked()).isTrue();
        assertThat(score.rulesApplied()).containsExactly("multi_trigger", "strong_check");
        assertThat(score.checks())
                .allMatch(check -> Boolean.TRUE.equals(check.details().get("mocked")));
        assertThat(score.checks().stream().mapToDouble(CheckContribution::contribution).sum())
                .isCloseTo(86.0, within(0.5));
    }

    @Test
    void borderlineMockProfileStaysMedium() {
        ScoringProperties properties = properties(0.5);
        MockModeContext context = new MockModeContext();
        DefaultScoringEngine engine = new DefaultScoringEngine(List.of(
                new MockBurstCheck(properties, context),
                new MockFreshAccountsCheck(properties, context),
                new MockEngagementCheck(properties, context),
                new MockActorOverlapCheck(properties, context),
                new MockCircadianCheck(properties, context)), properties, CLOCK);

        RepoScore score = context.callWith(MockMode.BORDERLINE, () -> engine.score(SNAPSHOT));

        assertThat(score.score()).isEqualTo(36);
        assertThat(score.level()).isEqualTo(ScoreLevel.MEDIUM);
        assertThat(score.rulesApplied()).isEmpty();
    }

    @Test
    void lowConfidenceCheckIsExcludedAndRenormalized() {
        DefaultScoringEngine engine = new DefaultScoringEngine(List.of(
                stub(CheckIds.BURST, 1.0, 0.2),
                stub(CheckIds.FRESH_ACCOUNTS, 0.10, 0.9),
                stub(CheckIds.ENGAGEMENT, 0.15, 0.9),
                stub(CheckIds.ACTOR_OVERLAP, 0.05, 0.9),
                stub(CheckIds.CIRCADIAN, 0.10, 0.9)), properties(0.5), CLOCK);

        RepoScore score = engine.score(SNAPSHOT);
        CheckContribution burst = score.checks().getFirst();

        assertThat(score.score()).isEqualTo(10);
        assertThat(score.confidence()).isEqualTo(0.9);
        assertThat(burst.active()).isFalse();
        assertThat(burst.contribution()).isZero();
        assertThat(burst.score()).isEqualTo(1.0);
    }

    @Test
    void failingCheckIsIsolated() {
        RepoCheck failing = new RepoCheck() {
            @Override
            public String id() {
                return CheckIds.CIRCADIAN;
            }

            @Override
            public CheckResult evaluate(RepoSnapshot snapshot) {
                throw new IllegalStateException("boom");
            }
        };
        DefaultScoringEngine engine = new DefaultScoringEngine(List.of(
                stub(CheckIds.BURST, 0.10, 0.9),
                stub(CheckIds.FRESH_ACCOUNTS, 0.10, 0.9),
                stub(CheckIds.ENGAGEMENT, 0.15, 0.9),
                stub(CheckIds.ACTOR_OVERLAP, 0.05, 0.9),
                failing), properties(0.5), CLOCK);

        RepoScore score = engine.score(SNAPSHOT);
        CheckContribution circadian = score.checks().getLast();

        assertThat(score.score()).isEqualTo(10);
        assertThat(score.checks()).hasSize(5);
        assertThat(circadian.active()).isFalse();
        assertThat(circadian.error()).isEqualTo("boom");
    }

    @Test
    void rejectsDuplicateCheckIds() {
        assertThatThrownBy(() -> new DefaultScoringEngine(List.of(
                stub(CheckIds.BURST, 0.1, 0.9),
                stub(CheckIds.BURST, 0.1, 0.9)), properties(0.5), CLOCK))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Duplicate");
    }

    private static ScoringProperties properties(double minConfidence) {
        return new ScoringProperties(MockMode.HEALTHY, minConfidence, WEIGHTS, null, null);
    }

    private static RepoCheck stub(String id, double score, double confidence) {
        return new RepoCheck() {
            @Override
            public String id() {
                return id;
            }

            @Override
            public CheckResult evaluate(RepoSnapshot snapshot) {
                return new CheckResult(id, score, confidence, Map.of());
            }
        };
    }
}
