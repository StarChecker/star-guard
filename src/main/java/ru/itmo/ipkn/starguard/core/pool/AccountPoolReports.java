package ru.itmo.ipkn.starguard.core.pool;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.Environment;
import ru.itmo.ipkn.starguard.core.config.AccountPoolProperties;
import ru.itmo.ipkn.starguard.core.config.ScoringProperties;
import ru.itmo.ipkn.starguard.core.domain.StarTimeline;
import ru.itmo.ipkn.starguard.core.scoring.*;
import ru.itmo.ipkn.starguard.core.service.RepoReport;
import java.time.Clock;
import java.time.Duration;
import java.util.*;

/** Additional one-sided evidence: cannot dilute the existing risk score. */
public class AccountPoolReports {
    public static final String CHECK_ID = "suspect_accounts";
    private static final Logger log = LoggerFactory.getLogger(AccountPoolReports.class);
    private final AccountPoolStore store;
    private final AccountPoolProperties p;
    private final Clock clock;
    private final ScoringProperties scoring;
    private final ScoringProperties githubScoring;
    public AccountPoolReports(AccountPoolStore store, AccountPoolProperties p, Clock clock,
                              ScoringProperties scoring, Environment environment) {
        this.store = store; this.p = p; this.clock = clock; this.scoring = scoring;
        this.githubScoring = Binder.get(environment).bind("app.scoring.github", Bindable.of(ScoringProperties.class))
                .orElse(scoring);
    }
    public RepoReport enrich(RepoReport report, String repoNodeId) {
        if (report.score().mocked()) return report;
        try {
            var evidence = store.evidence(report.repo().fullName(), repoNodeId, report.window().from(), report.window().to(),
                    clock.instant().minus(p.staleAfter()));
            return apply(report, evidence, null);
        } catch (RuntimeException e) {
            log.warn("Account pool unavailable for report {}", report.repo().fullName(), e);
            return apply(report, new AccountPoolStore.Evidence(0, List.of()), "pool_unavailable");
        }
    }
    private RepoReport apply(RepoReport report, AccountPoolStore.Evidence evidence, String error) {
        int matches = evidence.times().size();
        boolean active = error == null && matches >= p.minMatches();
        int floor = active ? (int) Math.round(p.maxScoreFloor() * Math.min(1.0,
                matches / (double) p.fullSignalMatches())) : 0;
        var old = report.score();
        int value = Math.max(old.score(), floor);
        var details = new HashMap<String, Object>();
        details.put("matched_accounts", matches);
        details.put("pool_size", evidence.poolSize());
        details.put("sample_limit_per_account", 100);
        details.put("partial_coverage", true);
        details.put("score_floor", floor);
        // Do not divide by archive samples or net growth: neither measures all stars added in the window.
        details.put("reason", error != null ? error : evidence.poolSize() == 0 ? "pool_empty"
                : matches == 0 ? "no_pool_matches" : "sampled_public_profiles");
        var checks = new ArrayList<>(old.checks());
        checks.add(new CheckContribution(CHECK_ID, floor / 100.0, active ? 0.7 : 0,
                0, value - old.score(), active, error, details));
        var rules = new ArrayList<>(old.rulesApplied());
        if (value > old.score()) rules.add("suspect_accounts_floor");
        var levels = (report.source().type() == ru.itmo.ipkn.starguard.core.domain.DataSource.Type.GITHUB_API
                ? githubScoring : scoring).levels();
        var level = value > old.score()
                ? ScoreLevel.fromScore(value, levels.medium(), levels.high(), levels.critical()) : old.level();
        double confidence = active && (old.insufficientData() || value > old.score())
                ? (old.insufficientData() ? 0.7 : Math.min(0.7, old.confidence())) : old.confidence();
        var score = new RepoScore(value, level, confidence,
                old.insufficientData() && !active, checks, old.mocked(), rules, old.computedAt());
        var timeline = report.starsTimeline();
        var points = timeline.points().stream().map(point -> {
            var end = point.at().plus(Duration.ofMinutes(timeline.bucketMinutes()));
            int count = (int) evidence.times().stream().filter(t -> !t.isBefore(point.at()) && t.isBefore(end)).count();
            return new StarTimeline.Point(point.at(), point.stars(), point.suspicious(), count);
        }).toList();
        // Keep this measured series separate from estimated burst excess; overlap is unknown.
        var enriched = new StarTimeline(timeline.bucketMinutes(), timeline.estimated(), points,
                timeline.suspiciousStars(), timeline.suspiciousByCheck());
        return new RepoReport(report.repo(), report.window(), report.stats(), score, report.archive(), enriched, report.source());
    }
}
