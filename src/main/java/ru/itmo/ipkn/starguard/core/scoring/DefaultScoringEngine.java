package ru.itmo.ipkn.starguard.core.scoring;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import ru.itmo.ipkn.starguard.core.config.ScoringProperties;
import ru.itmo.ipkn.starguard.core.domain.RepoSnapshot;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Сборка итогового score по алгоритму CHECKS.md §4:
 * взвешенное среднее по активным проверкам, ренормировка весов,
 * два правила усиления и уровни из конфигурации.
 */
@Component
public class DefaultScoringEngine implements ScoringEngine {

    private static final Logger log = LoggerFactory.getLogger(DefaultScoringEngine.class);

    private static final Comparator<RepoCheck> CHECK_ORDER = Comparator
            .comparingInt((RepoCheck check) -> {
                int index = CheckIds.ALL.indexOf(check.id());
                return index >= 0 ? index : Integer.MAX_VALUE;
            })
            .thenComparing(RepoCheck::id);

    private final List<RepoCheck> checks;
    private final ScoringProperties properties;
    private final Clock clock;

    public DefaultScoringEngine(List<RepoCheck> checks, ScoringProperties properties, Clock clock) {
        this.checks = checks.stream().sorted(CHECK_ORDER).toList();
        this.properties = properties;
        this.clock = clock;

        Set<String> ids = new HashSet<>();
        for (RepoCheck check : this.checks) {
            if (!ids.add(check.id())) {
                throw new IllegalStateException("Duplicate RepoCheck id: " + check.id());
            }
        }
        double weightSum = this.checks.stream()
                .mapToDouble(check -> properties.weight(check.id()))
                .sum();
        if (!this.checks.isEmpty() && Math.abs(weightSum - 1.0) > 1e-6) {
            log.warn("Scoring weights sum to {} instead of 1.0; the engine will renormalize active weights",
                    weightSum);
        }
    }

    @Override
    public RepoScore score(RepoSnapshot snapshot) {
        List<EvaluatedCheck> evaluated = evaluate(snapshot);

        List<EvaluatedCheck> active = evaluated.stream()
                .filter(result -> result.result() != null)
                .filter(result -> result.result().confidence() >= properties.minConfidence())
                .toList();
        Set<String> activeIds = new HashSet<>();
        active.forEach(result -> activeIds.add(result.check().id()));

        double weightSum = active.stream()
                .mapToDouble(result -> properties.weight(result.check().id()))
                .sum();
        boolean insufficientData = active.isEmpty() || weightSum <= 0.0;

        int score = 0;
        double confidence = 0.0;
        List<String> rulesApplied = new ArrayList<>();

        if (!insufficientData) {
            double raw = active.stream()
                    .mapToDouble(result -> properties.weight(result.check().id())
                            * result.result().score())
                    .sum() / weightSum;
            score = (int) Math.round(100.0 * raw);

            ScoringProperties.Amplification amplification = properties.amplification();

            long hotChecks = active.stream()
                    .filter(result -> result.result().score()
                            >= amplification.multiTrigger().checkScoreAtLeast())
                    .count();
            if (hotChecks >= amplification.multiTrigger().minChecks()) {
                score = Math.max(score, amplification.multiTrigger().floor());
                rulesApplied.add("multi_trigger");
            }

            boolean strongCheck = active.stream().anyMatch(result ->
                    amplification.strongCheck().checkIds().contains(result.check().id())
                            && result.result().score() >= amplification.strongCheck().checkScoreAtLeast()
                            && result.result().confidence()
                            >= amplification.strongCheck().minConfidence());
            if (strongCheck) {
                score = Math.max(score, amplification.strongCheck().floor());
                rulesApplied.add("strong_check");
            }

            score = Math.max(0, Math.min(100, score));
            confidence = active.stream()
                    .mapToDouble(result -> result.result().confidence())
                    .min()
                    .orElse(0.0);
        }

        List<CheckContribution> contributions = new ArrayList<>(evaluated.size());
        for (EvaluatedCheck result : evaluated) {
            String checkId = result.check().id();
            boolean isActive = activeIds.contains(checkId);
            CheckResult checkResult = result.result();
            double checkScore = checkResult == null ? 0.0 : checkResult.score();
            double checkConfidence = checkResult == null ? 0.0 : checkResult.confidence();
            double weight = properties.weight(checkId);
            double contribution = 0.0;
            if (isActive && !insufficientData) {
                contribution = round1(100.0 * weight * checkScore / weightSum);
            }
            contributions.add(new CheckContribution(
                    checkId,
                    checkScore,
                    checkConfidence,
                    weight,
                    contribution,
                    isActive,
                    result.error(),
                    checkResult == null ? Map.of() : checkResult.details()));
        }

        ScoringProperties.LevelThresholds levels = properties.levels();
        ScoreLevel level = insufficientData
                ? ScoreLevel.LOW
                : ScoreLevel.fromScore(score, levels.medium(), levels.high(), levels.critical());
        boolean mocked = contributions.stream()
                .anyMatch(contribution -> Boolean.TRUE.equals(contribution.details().get("mocked")));

        return new RepoScore(
                score,
                level,
                confidence,
                insufficientData,
                contributions,
                mocked,
                rulesApplied,
                clock.instant());
    }

    private List<EvaluatedCheck> evaluate(RepoSnapshot snapshot) {
        List<EvaluatedCheck> results = new ArrayList<>(checks.size());
        for (RepoCheck check : checks) {
            try {
                CheckResult result = check.evaluate(snapshot);
                if (result == null) {
                    throw new IllegalStateException("check returned null");
                }
                if (!check.id().equals(result.checkId())) {
                    throw new IllegalStateException("check returned id '%s' instead of '%s'"
                            .formatted(result.checkId(), check.id()));
                }
                results.add(new EvaluatedCheck(check, result, null));
            } catch (RuntimeException e) {
                log.warn("RepoCheck '{}' failed for {}", check.id(), snapshot.repo().fullName(), e);
                String message = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
                results.add(new EvaluatedCheck(check, null, message));
            }
        }
        return results;
    }

    private static double round1(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    private record EvaluatedCheck(RepoCheck check, CheckResult result, String error) {
    }
}
