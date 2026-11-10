package ru.itmo.ipkn.starguard.core.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import ru.itmo.ipkn.starguard.core.scoring.CheckMode;

/**
 * Настройки C4 «Пересечение акторов».
 *
 * @param mode                   mock | real; по умолчанию наследует {@code app.scoring.mode}
 * @param candidatePool          сколько репозиториев с общими акторами сравнивать по Jaccard (топ по числу общих)
 * @param jaccardFloor           Jmax, при котором и ниже которого вклад Jaccard равен нулю
 * @param jaccardSpan            ширина шкалы Jaccard: при {@code floor + span} вклад равен 1
 * @param jaccardWeight          доля Jaccard в s4; остальное — multiShare
 * @param multiRepoThreshold     актор «мультизвёздный», если за окно поставил звёзды стольким репозиториям (с текущим)
 * @param multiFloor             multiShare, при котором и ниже которого вклад равен нулю
 * @param multiSpan              ширина шкалы multiShare
 * @param actorsGate             число акторов-звёзд, начиная с которого gate = 1
 * @param minActorsForConfidence меньше акторов — данных недостаточно, проверка неактивна
 * @param confidence             confidence при полном индексе за всё окно
 * @param truncatedPenalty       множитель confidence, если индекс обрезан лимитом памяти
 * @param topOverlaps            сколько пересекающихся репозиториев показывать в explain
 * @param suspiciousJaccard      звёзды общих акторов с репозиторием, у которого Jaccard не ниже порога,
 *                               помечаются подозрительными на таймлайне
 */
@ConfigurationProperties(prefix = "app.scoring.checks.actor-overlap")
public record ActorOverlapProperties(
        CheckMode mode,
        Integer candidatePool,
        Double jaccardFloor,
        Double jaccardSpan,
        Double jaccardWeight,
        Integer multiRepoThreshold,
        Double multiFloor,
        Double multiSpan,
        Integer actorsGate,
        Integer minActorsForConfidence,
        Double confidence,
        Double truncatedPenalty,
        Integer topOverlaps,
        Double suspiciousJaccard
) {

    /** Свойство, по которому выбирается реализация C4 (реальная или мок). */
    public static final String MODE_PROPERTY = "app.scoring.checks.actor-overlap.mode";

    private static final String PREFIX = "app.scoring.checks.actor-overlap.";

    public ActorOverlapProperties {
        mode = mode == null ? CheckMode.MOCK : mode;
        candidatePool = candidatePool == null ? 500 : candidatePool;
        jaccardFloor = jaccardFloor == null ? 0.05 : jaccardFloor;
        jaccardSpan = jaccardSpan == null ? 0.45 : jaccardSpan;
        jaccardWeight = jaccardWeight == null ? 0.5 : jaccardWeight;
        multiRepoThreshold = multiRepoThreshold == null ? 3 : multiRepoThreshold;
        multiFloor = multiFloor == null ? 0.1 : multiFloor;
        multiSpan = multiSpan == null ? 0.4 : multiSpan;
        actorsGate = actorsGate == null ? 10 : actorsGate;
        minActorsForConfidence = minActorsForConfidence == null ? 5 : minActorsForConfidence;
        confidence = confidence == null ? 0.8 : confidence;
        truncatedPenalty = truncatedPenalty == null ? 0.75 : truncatedPenalty;
        topOverlaps = topOverlaps == null ? 3 : topOverlaps;
        suspiciousJaccard = suspiciousJaccard == null ? 0.25 : suspiciousJaccard;

        if (candidatePool < 1) {
            throw new IllegalArgumentException(PREFIX + "candidate-pool must be >= 1");
        }
        requireUnit(jaccardFloor, "jaccard-floor");
        requirePositive(jaccardSpan, "jaccard-span");
        requireUnit(jaccardWeight, "jaccard-weight");
        if (multiRepoThreshold < 2) {
            throw new IllegalArgumentException(PREFIX + "multi-repo-threshold must be >= 2");
        }
        requireUnit(multiFloor, "multi-floor");
        requirePositive(multiSpan, "multi-span");
        if (actorsGate < 1) {
            throw new IllegalArgumentException(PREFIX + "actors-gate must be >= 1");
        }
        if (minActorsForConfidence < 0) {
            throw new IllegalArgumentException(PREFIX + "min-actors-for-confidence must be >= 0");
        }
        requireUnit(confidence, "confidence");
        requireUnit(truncatedPenalty, "truncated-penalty");
        requireUnit(suspiciousJaccard, "suspicious-jaccard");
        if (topOverlaps < 0) {
            throw new IllegalArgumentException(PREFIX + "top-overlaps must be >= 0");
        }
    }

    public static ActorOverlapProperties defaults() {
        return new ActorOverlapProperties(
                null, null, null, null, null, null, null, null, null, null, null, null, null, null);
    }

    private static void requireUnit(double value, String name) {
        if (value < 0.0 || value > 1.0) {
            throw new IllegalArgumentException(PREFIX + name + " must be within [0, 1]");
        }
    }

    private static void requirePositive(double value, String name) {
        if (value <= 0.0) {
            throw new IllegalArgumentException(PREFIX + name + " must be > 0");
        }
    }
}
