package ru.itmo.ipkn.starguard.core.scoring;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import ru.itmo.ipkn.starguard.core.config.EngagementProperties;
import ru.itmo.ipkn.starguard.core.domain.EventCounts;
import ru.itmo.ipkn.starguard.core.domain.RepoSnapshot;

import java.util.HashMap;
import java.util.Map;

/**
 * C3 «Дисбаланс звёзды/активность»: много звёзд при почти нулевой остальной
 * активности репозитория — типичный признак накрутки.
 *
 * <pre>
 * engagement = (forks + issues + pulls + pushes) / max(stars, 1)
 * s3         = clamp01((threshold − engagement) / threshold)
 * gate       = min(1, stars / starsGate)
 * score      = s3 × gate
 * </pre>
 *
 * Пока звёзд меньше {@code min-stars-for-confidence}, confidence = 0:
 * по паре звёзд о дисбалансе судить нельзя, и движок не включает C3 в score.
 */
@Component
@ConditionalOnProperty(name = EngagementProperties.MODE_PROPERTY, havingValue = "real")
public class EngagementCheck implements RepoCheck {

    private final EngagementProperties properties;

    public EngagementCheck(EngagementProperties properties) {
        this.properties = properties;
    }

    @Override
    public String id() {
        return CheckIds.ENGAGEMENT;
    }

    @Override
    public CheckResult evaluate(RepoSnapshot snapshot) {
        EventCounts counts = snapshot.counts();
        int stars = counts.stars();
        int activity = counts.forks() + counts.issues() + counts.pullRequests() + counts.pushes();

        double threshold = properties.engagementThreshold();
        double engagement = (double) activity / Math.max(stars, 1);
        double s3 = Math.clamp((threshold - engagement) / threshold, 0.0, 1.0);
        double gate = Math.min(1.0, (double) stars / properties.starsGate());
        double score = s3 * gate;

        boolean enoughStars = stars >= properties.minStarsForConfidence();
        double confidence = enoughStars ? properties.confidence() : 0.0;

        Map<String, Object> details = new HashMap<>();
        details.put("mocked", false);
        details.put("stars", stars);
        details.put("activity", activity);
        details.put("engagement", round3(engagement));
        details.put("threshold", threshold);
        details.put("s3", round3(s3));
        details.put("gate", round3(gate));
        details.put("stars_gate", properties.starsGate());
        if (!enoughStars) {
            details.put("reason", "too_few_stars");
            details.put("min_stars_for_confidence", properties.minStarsForConfidence());
        }
        return new CheckResult(id(), score, confidence, details);
    }

    private static double round3(double value) {
        return Math.round(value * 1000.0) / 1000.0;
    }
}
