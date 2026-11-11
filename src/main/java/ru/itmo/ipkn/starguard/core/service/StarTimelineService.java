package ru.itmo.ipkn.starguard.core.service;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import ru.itmo.ipkn.starguard.core.domain.ActorProfile;
import ru.itmo.ipkn.starguard.core.domain.RepoSnapshot;
import ru.itmo.ipkn.starguard.core.domain.StarMark;
import ru.itmo.ipkn.starguard.core.domain.StarTimeline;
import ru.itmo.ipkn.starguard.core.domain.TimeWindow;
import ru.itmo.ipkn.starguard.core.scoring.ActorOverlapCheck;
import ru.itmo.ipkn.starguard.core.scoring.CheckContribution;
import ru.itmo.ipkn.starguard.core.scoring.CheckIds;
import ru.itmo.ipkn.starguard.core.scoring.FreshAccountsCheck;
import ru.itmo.ipkn.starguard.core.scoring.RepoScore;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Строит таймлайн звёзд с разметкой подозрительных для графика в расширении.
 * <p>
 * Звезду помечает подозрительной реальная (не мок) и активная проверка с ненулевым score,
 * которая умеет назвать «плохих» акторов: C4 (аккаунты фермы) и C2 (свежие/пустые аккаунты).
 * Если проверка в целом ничего не нашла, единичные совпадения не подсвечиваются — это шум.
 * C1 добавится сюда, когда появится реальная реализация с минутами всплесков.
 */
@Service
public class StarTimelineService {

    private final ObjectProvider<ActorOverlapCheck> actorOverlap;
    private final ObjectProvider<FreshAccountsCheck> freshAccounts;

    public StarTimelineService(
            ObjectProvider<ActorOverlapCheck> actorOverlap,
            ObjectProvider<FreshAccountsCheck> freshAccounts) {
        this.actorOverlap = actorOverlap;
        this.freshAccounts = freshAccounts;
    }

    public StarTimeline build(RepoSnapshot snapshot, RepoScore score, List<StarMark> stars) {
        TimeWindow window = snapshot.window();
        int bucketMinutes = bucketMinutes(window.hours());
        int totalMinutes = window.hours() * 60;
        int buckets = (totalMinutes + bucketMinutes - 1) / bucketMinutes;

        int[] starsPerBucket = new int[buckets];
        int[] perMinute = snapshot.starsByMinute();
        for (int minute = 0; minute < Math.min(perMinute.length, totalMinutes); minute++) {
            starsPerBucket[minute / bucketMinutes] += perMinute[minute];
        }

        Map<Long, Set<String>> flags = flaggedActors(snapshot, score, stars);
        int[] suspiciousPerBucket = new int[buckets];
        Map<Integer, int[]> hourTotals = new TreeMap<>();
        Map<String, Integer> byCheck = new HashMap<>();
        int suspiciousStars = 0;

        for (StarMark mark : stars) {
            long minute = Duration.between(window.from(), mark.at()).toMinutes();
            if (minute < 0 || minute >= totalMinutes) {
                continue;
            }
            Set<String> reasons = flags.get(mark.actorId());
            boolean flagged = reasons != null;
            if (flagged) {
                suspiciousStars++;
                reasons.forEach(check -> byCheck.merge(check, 1, Integer::sum));
            }
            if (mark.hourResolution()) {
                int[] totals = hourTotals.computeIfAbsent((int) (minute / 60), hour -> new int[2]);
                totals[0]++;
                if (flagged) {
                    totals[1]++;
                }
            } else if (flagged) {
                suspiciousPerBucket[(int) (minute / bucketMinutes)]++;
            }
        }

        // В режиме индекса акторы известны по часам: долю подозрительных часа
        // раскладываем по точкам часа пропорционально их звёздам.
        boolean estimated = false;
        for (Map.Entry<Integer, int[]> entry : hourTotals.entrySet()) {
            int[] totals = entry.getValue();
            if (totals[1] == 0 || totals[0] == 0) {
                continue;
            }
            estimated = true;
            double share = (double) totals[1] / totals[0];
            int firstBucket = entry.getKey() * 60 / bucketMinutes;
            int lastBucket = Math.min(buckets, (entry.getKey() + 1) * 60 / bucketMinutes);
            for (int bucket = firstBucket; bucket < lastBucket; bucket++) {
                suspiciousPerBucket[bucket] += (int) Math.round(starsPerBucket[bucket] * share);
            }
        }

        List<StarTimeline.Point> points = new ArrayList<>(buckets);
        for (int bucket = 0; bucket < buckets; bucket++) {
            points.add(new StarTimeline.Point(
                    window.from().plus(Duration.ofMinutes((long) bucket * bucketMinutes)),
                    starsPerBucket[bucket],
                    Math.min(starsPerBucket[bucket], suspiciousPerBucket[bucket])));
        }
        return new StarTimeline(bucketMinutes, estimated, points, suspiciousStars, byCheck);
    }

    static int bucketMinutes(int hours) {
        if (hours <= 6) {
            return 10;
        }
        if (hours <= 12) {
            return 20;
        }
        if (hours <= 24) {
            return 30;
        }
        // длинные окна истории GitHub: не больше ~70–120 точек на графике
        if (hours <= 72) {
            return 60;
        }
        if (hours <= 168) {
            return 180;
        }
        if (hours <= 360) {
            return 360;
        }
        return 720;
    }

    private Map<Long, Set<String>> flaggedActors(
            RepoSnapshot snapshot, RepoScore score, List<StarMark> stars) {
        Map<Long, Set<String>> flags = new HashMap<>();
        if (stars.isEmpty()) {
            return flags;
        }

        ActorOverlapCheck overlap = actorOverlap.getIfAvailable();
        if (overlap != null && isRealAndActive(score, CheckIds.ACTOR_OVERLAP)) {
            for (Long actor : overlap.flaggedActors(snapshot)) {
                flags.computeIfAbsent(actor, id -> new HashSet<>()).add(CheckIds.ACTOR_OVERLAP);
            }
        }

        FreshAccountsCheck fresh = freshAccounts.getIfAvailable();
        if (fresh != null && isRealAndActive(score, CheckIds.FRESH_ACCOUNTS)) {
            Set<String> freshHashes = fresh.freshActorHashes(snapshot);
            if (!freshHashes.isEmpty()) {
                Set<Long> actors = new HashSet<>();
                stars.forEach(mark -> actors.add(mark.actorId()));
                for (long actor : actors) {
                    if (freshHashes.contains(ActorProfile.hashOf(actor))) {
                        flags.computeIfAbsent(actor, id -> new HashSet<>()).add(CheckIds.FRESH_ACCOUNTS);
                    }
                }
            }
        }
        return flags;
    }

    private static boolean isRealAndActive(RepoScore score, String checkId) {
        return score.checks().stream()
                .filter(check -> check.checkId().equals(checkId))
                .anyMatch(check -> check.active() && check.score() > 0 && !isMocked(check));
    }

    private static boolean isMocked(CheckContribution check) {
        return Boolean.TRUE.equals(check.details().get("mocked"));
    }
}
