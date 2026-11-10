package ru.itmo.ipkn.starguard.core.scoring;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import ru.itmo.ipkn.starguard.core.config.ActorOverlapProperties;
import ru.itmo.ipkn.starguard.core.domain.RepoSnapshot;
import ru.itmo.ipkn.starguard.core.domain.StarIndex;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * C4 «Пересечение акторов»: ферма из одних и тех же аккаунтов ставит звёзды
 * сразу нескольким репозиториям.
 *
 * <pre>
 * A_r        = акторы, поставившие звезду репозиторию за окно (из глобального {@link StarIndex})
 * J(r, p)    = |A_r ∩ A_p| / |A_r ∪ A_p| по кандидатам p — топ-N репозиториев по числу общих акторов
 * multiShare = доля A_r, поставивших за окно звёзды ≥ multiRepoThreshold репозиториям (вместе с r)
 * s4         = w·clamp01((Jmax − jFloor)/jSpan) + (1 − w)·clamp01((multiShare − mFloor)/mSpan)
 * gate       = min(1, |A_r| / actorsGate)
 * score      = s4 × gate
 * </pre>
 *
 * Кандидаты выбираются среди репозиториев, у которых есть хотя бы один общий актор:
 * у остальных J = 0, сравнивать их бессмысленно. Так проверка ловит и маленькие
 * репозитории фермы, которые не попали бы в глобальный топ по звёздам.
 * <p>
 * Confidence = 0, если индекса нет или акторов меньше {@code min-actors-for-confidence};
 * иначе базовый confidence × доля окна, покрытая индексом (× штраф, если индекс обрезан).
 */
@Component
@ConditionalOnProperty(name = ActorOverlapProperties.MODE_PROPERTY, havingValue = "real")
public class ActorOverlapCheck implements RepoCheck {

    /** Jaccard по убыванию, при равенстве — больше общих акторов, затем по имени (детерминизм). */
    private static final Comparator<RankedOverlap> RANKING = Comparator
            .comparingDouble(RankedOverlap::jaccard).reversed()
            .thenComparing(Comparator.comparingInt((RankedOverlap ranked) -> ranked.overlap().shared()).reversed())
            .thenComparing((RankedOverlap ranked) -> ranked.overlap().repo());

    private final ActorOverlapProperties properties;

    public ActorOverlapCheck(ActorOverlapProperties properties) {
        this.properties = properties;
    }

    @Override
    public String id() {
        return CheckIds.ACTOR_OVERLAP;
    }

    @Override
    public CheckResult evaluate(RepoSnapshot snapshot) {
        StarIndex index = snapshot.starIndex();
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("mocked", false);

        if (!index.available()) {
            details.put("reason", "no_cross_repo_index");
            return new CheckResult(id(), 0.0, 0.0, details);
        }

        String targetKey = StarIndex.normalize(snapshot.repo().fullName());
        long[] targetActors = index.actors(targetKey);
        int actorCount = targetActors.length;

        // Для каждого актора A_r — в скольких ещё репозиториях он поставил звезду;
        // для каждого репозитория — сколько у него общих акторов с A_r.
        int[] otherRepos = new int[actorCount];
        List<Overlap> overlaps = new ArrayList<>();
        if (actorCount > 0) {
            index.forEachRepo((repo, actors) -> {
                if (repo.equals(targetKey)) {
                    return;
                }
                int shared = 0;
                for (long actor : actors) {
                    int position = Arrays.binarySearch(targetActors, actor);
                    if (position >= 0) {
                        shared++;
                        otherRepos[position]++;
                    }
                }
                if (shared > 0) {
                    overlaps.add(new Overlap(repo, shared, actors.length));
                }
            });
        }

        List<Overlap> candidates = overlaps.stream()
                .sorted(Comparator.comparingInt(Overlap::shared).reversed()
                        .thenComparing(Overlap::repo))
                .limit(properties.candidatePool())
                .toList();
        List<RankedOverlap> ranked = candidates.stream()
                .map(overlap -> new RankedOverlap(overlap, jaccard(actorCount, overlap)))
                .sorted(RANKING)
                .toList();
        double jaccardMax = ranked.isEmpty() ? 0.0 : ranked.getFirst().jaccard();

        int needOtherRepos = properties.multiRepoThreshold() - 1;
        int multiRepoActors = 0;
        for (int count : otherRepos) {
            if (count >= needOtherRepos) {
                multiRepoActors++;
            }
        }
        double multiShare = actorCount == 0 ? 0.0 : (double) multiRepoActors / actorCount;

        double sJaccard = Math.clamp(
                (jaccardMax - properties.jaccardFloor()) / properties.jaccardSpan(), 0.0, 1.0);
        double sMulti = Math.clamp(
                (multiShare - properties.multiFloor()) / properties.multiSpan(), 0.0, 1.0);
        double weight = properties.jaccardWeight();
        double s4 = weight * sJaccard + (1.0 - weight) * sMulti;
        double gate = Math.min(1.0, (double) actorCount / properties.actorsGate());
        double score = s4 * gate;

        int windowHours = snapshot.window().hours();
        double coverage = Math.min(1.0, (double) index.hoursCovered() / windowHours);
        boolean enoughActors = actorCount >= properties.minActorsForConfidence();
        double confidence = 0.0;
        if (enoughActors) {
            confidence = properties.confidence() * coverage
                    * (index.truncated() ? properties.truncatedPenalty() : 1.0);
        }

        details.put("star_actors", actorCount);
        details.put("jaccard_max", round3(jaccardMax));
        details.put("s_jaccard", round3(sJaccard));
        details.put("multi_share", round3(multiShare));
        details.put("multi_repo_actors", multiRepoActors);
        details.put("multi_repo_threshold", properties.multiRepoThreshold());
        details.put("s_multi", round3(sMulti));
        details.put("s4", round3(s4));
        details.put("gate", round3(gate));
        details.put("actors_gate", properties.actorsGate());
        details.put("overlapping_repos", overlaps.size());
        details.put("candidates_compared", candidates.size());
        details.put("top_overlaps", ranked.stream()
                .limit(properties.topOverlaps())
                .map(ActorOverlapCheck::toDetails)
                .toList());
        details.put("index_hours", index.hoursCovered());
        details.put("window_hours", windowHours);
        details.put("index_repos", index.repoCount());
        details.put("index_truncated", index.truncated());
        if (!enoughActors) {
            details.put("reason", "too_few_star_actors");
            details.put("min_actors_for_confidence", properties.minActorsForConfidence());
        }
        return new CheckResult(id(), score, confidence, details);
    }

    private static double jaccard(int targetSize, Overlap overlap) {
        int union = targetSize + overlap.repoActors() - overlap.shared();
        return union <= 0 ? 0.0 : (double) overlap.shared() / union;
    }

    private static Map<String, Object> toDetails(RankedOverlap ranked) {
        Map<String, Object> item = new HashMap<>();
        item.put("repo", ranked.overlap().repo());
        item.put("jaccard", round3(ranked.jaccard()));
        item.put("shared_actors", ranked.overlap().shared());
        item.put("repo_star_actors", ranked.overlap().repoActors());
        return Map.copyOf(item);
    }

    private static double round3(double value) {
        return Math.round(value * 1000.0) / 1000.0;
    }

    private record Overlap(String repo, int shared, int repoActors) {
    }

    private record RankedOverlap(Overlap overlap, double jaccard) {
    }
}
