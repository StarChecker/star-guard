package ru.itmo.ipkn.starguard.core.pool;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import ru.itmo.ipkn.starguard.core.archive.ArchiveFetchResult;
import ru.itmo.ipkn.starguard.core.config.ScoringProperties;
import ru.itmo.ipkn.starguard.core.domain.*;
import ru.itmo.ipkn.starguard.core.scoring.*;
import ru.itmo.ipkn.starguard.core.service.RepoReport;

import java.time.Clock;
import java.time.Duration;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static ru.itmo.ipkn.starguard.core.pool.PoolFixtures.*;

class AccountPoolReportsTest {
    private final AccountPoolStore store = mock(AccountPoolStore.class);
    private final AccountPoolReports reports = new AccountPoolReports(store, defaults(),
            Clock.fixed(NOW, ZoneOffset.UTC), new ScoringProperties(null, null, null, null, null),
            new MockEnvironment());

    @Test
    void zeroMatchesDoNotDiluteExistingScoreOrConfidence() {
        evidence(new AccountPoolStore.Evidence(50, List.of()));
        var result = reports.enrich(report(80, false), "R1");
        assertThat(result.score().score()).isEqualTo(80);
        assertThat(result.score().confidence()).isEqualTo(0.9);
        assertThat(result.score().checks().getLast().active()).isFalse();
        assertThat(result.score().checks().getLast().details()).containsEntry("reason", "no_pool_matches");
    }

    @Test
    void matchesRaiseScoreAndAppearSeparatelyFromBurstEstimate() {
        evidence(new AccountPoolStore.Evidence(100, Collections.nCopies(100, NOW.minusSeconds(1800))));
        var result = reports.enrich(report(20, false), "R1");
        assertThat(result.score().score()).isEqualTo(70);
        assertThat(result.score().level()).isEqualTo(ScoreLevel.HIGH);
        assertThat(result.score().rulesApplied()).contains("suspect_accounts_floor");
        assertThat(result.score().checks().getLast().contribution()).isEqualTo(50);
        assertThat(result.starsTimeline().points().getFirst().suspectAccounts()).isEqualTo(100);
        assertThat(result.starsTimeline().points().getFirst().suspicious()).isEqualTo(2);
        assertThat(result.starsTimeline().suspiciousStars()).isEqualTo(2);
        verify(store).evidence("owner/repo", "R1", NOW.minusSeconds(3600), NOW, NOW.minus(Duration.ofDays(3)));
    }

    @Test
    void fewMatchesAreVisibleButInactiveAndHigherScoreIsNeverReduced() {
        evidence(new AccountPoolStore.Evidence(100, Collections.nCopies(4, NOW.minusSeconds(1800))));
        var result = reports.enrich(report(90, false), "R1");
        assertThat(result.score().score()).isEqualTo(90);
        assertThat(result.score().checks().getLast().active()).isFalse();
        assertThat(result.starsTimeline().points().getFirst().suspectAccounts()).isEqualTo(4);
    }

    @Test
    void activeEvidenceNeverLowersAnAlreadyHigherScore() {
        evidence(new AccountPoolStore.Evidence(100, Collections.nCopies(100, NOW.minusSeconds(1800))));
        var result = reports.enrich(report(90, false), "R1");
        assertThat(result.score().score()).isEqualTo(90);
        assertThat(result.score().confidence()).isEqualTo(0.9);
        assertThat(result.score().checks().getLast().active()).isTrue();
        assertThat(result.score().checks().getLast().contribution()).isZero();
        assertThat(result.score().rulesApplied()).doesNotContain("suspect_accounts_floor");
    }

    @Test
    void serializableDtoCarriesIndependentPoolSeries() {
        evidence(new AccountPoolStore.Evidence(100, Collections.nCopies(5, NOW.minusSeconds(1800))));
        var report = reports.enrich(report(20, false), "R1");
        var dto = new ru.itmo.ipkn.starguard.core.api.RepoReportMapper().toDto(report);
        assertThat(dto.starsTimeline().points().getFirst().suspectAccounts()).isEqualTo(5);
        assertThat(dto.starsTimeline().points().getFirst().suspicious()).isEqualTo(2);
    }

    @Test
    void databaseFailureLeavesScoreUnchangedAndExposesInactiveCheck() {
        when(store.evidence(anyString(), anyString(), any(), any(), any())).thenThrow(new IllegalStateException("offline"));
        var result = reports.enrich(report(35, false), "R1");
        assertThat(result.score().score()).isEqualTo(35);
        assertThat(result.score().checks().getLast().error()).isEqualTo("pool_unavailable");
        assertThat(result.score().checks().getLast().active()).isFalse();
    }

    @Test
    void mockReportsDoNotQueryRealPool() {
        var report = report(35, true);
        assertThat(reports.enrich(report, "R1")).isSameAs(report);
        verifyNoInteractions(store);
    }

    private void evidence(AccountPoolStore.Evidence value) {
        when(store.evidence(anyString(), anyString(), any(), any(), any())).thenReturn(value);
    }

    private RepoReport report(int value, boolean mocked) {
        var repo = new RepoRef("owner", "repo");
        var window = new TimeWindow(NOW.minusSeconds(3600), NOW, 1);
        var stats = new RepoStats(repo, window, new EventCounts(200, 200, 0, 0, 0, 0, 0), 0, 0, 0, List.of());
        var score = new RepoScore(value, ScoreLevel.fromScore(value, 30, 60, 85), 0.9, false,
                List.of(), mocked, List.of(), NOW);
        var timeline = new StarTimeline(60, false,
                List.of(new StarTimeline.Point(window.from(), 200, 2)), 2, Map.of("daily_burst", 2));
        return new RepoReport(repo, window, stats, score,
                new ArchiveFetchResult(List.of(), 0, 0, List.of(), 0, false), timeline,
                new DataSource(DataSource.Type.GITHUB_API, 200, 1000, window.from()));
    }
}
