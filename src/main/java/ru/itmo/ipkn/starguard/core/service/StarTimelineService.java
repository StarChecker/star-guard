package ru.itmo.ipkn.starguard.core.service;

import org.springframework.stereotype.Service;
import ru.itmo.ipkn.starguard.core.config.BurstProperties;
import ru.itmo.ipkn.starguard.core.domain.RepoSnapshot;
import ru.itmo.ipkn.starguard.core.domain.StarMark;
import ru.itmo.ipkn.starguard.core.domain.StarTimeline;
import ru.itmo.ipkn.starguard.core.domain.TimeWindow;
import ru.itmo.ipkn.starguard.core.scoring.BurstCheck;
import ru.itmo.ipkn.starguard.core.scoring.CheckContribution;
import ru.itmo.ipkn.starguard.core.scoring.CheckIds;
import ru.itmo.ipkn.starguard.core.scoring.RepoScore;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
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
 * C1 даёт отдельный интервал пика, не меняя число звёзд, отмеченных C2/C4.
 */
@Service
public class StarTimelineService {

    private final SuspiciousActorsService actorsService;
    private final BurstProperties burstProperties;

    public StarTimelineService(SuspiciousActorsService actorsService, BurstProperties burstProperties) {
        this.actorsService = actorsService;
        this.burstProperties = burstProperties;
    }

    public StarTimeline build(RepoSnapshot snapshot, RepoScore score, List<StarMark> stars) {
        return build(snapshot, score, stars, actorsService.flags(snapshot, score, stars));
    }

    public StarTimeline build(RepoSnapshot snapshot, RepoScore score, List<StarMark> stars,
                              Map<Long, Set<String>> flags) {
        TimeWindow window = snapshot.window();
        int bucketMinutes = bucketMinutes(window.hours());
        int totalMinutes = window.hours() * 60;
        int buckets = (totalMinutes + bucketMinutes - 1) / bucketMinutes;

        int[] starsPerBucket = new int[buckets];
        int[] perMinute = snapshot.starsByMinute();
        if (perMinute == null) return StarTimeline.empty(bucketMinutes);
        for (int minute = 0; minute < Math.min(perMinute.length, totalMinutes); minute++) {
            starsPerBucket[minute / bucketMinutes] += perMinute[minute];
        }

        int[] suspiciousPerBucket = new int[buckets];
        Map<Integer, int[]> hourTotals = new TreeMap<>();
        Map<String, Integer> byCheck = new HashMap<>();
        int suspiciousStars = 0;

        for (StarMark mark : stars) {
            long minute = Duration.between(window.from(), mark.at()).toMinutes();
            if (mark.at().isBefore(window.from()) || !mark.at().isBefore(window.to())) {
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
            int startMinute = entry.getKey() * 60;
            int endMinute = Math.min(perMinute.length, startMinute + 60);
            int cumulative = 0;
            int assigned = 0;
            // Работает и для бакетов длиннее часа; округление не теряет редкие звёзды.
            for (int minute = startMinute; minute < endMinute; minute++) {
                cumulative += perMinute[minute];
                int next = (int) Math.round(cumulative * share);
                suspiciousPerBucket[minute / bucketMinutes] += next - assigned;
                assigned = next;
            }
        }

        List<StarTimeline.Point> points = new ArrayList<>(buckets);
        for (int bucket = 0; bucket < buckets; bucket++) {
            points.add(new StarTimeline.Point(
                    window.from().plus(Duration.ofMinutes((long) bucket * bucketMinutes)),
                    starsPerBucket[bucket],
                    Math.min(starsPerBucket[bucket], suspiciousPerBucket[bucket])));
        }
        return new StarTimeline(bucketMinutes, estimated, points, suspiciousStars, byCheck,
                spikeIntervals(snapshot, score, perMinute));
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

    private List<StarTimeline.SpikeInterval> spikeIntervals(RepoSnapshot snapshot, RepoScore score, int[] minutes) {
        // В real-режиме берём точные details уже выполненного C1. Иначе считаем
        // те же наблюдаемые величины, но не выдаём mock-score за подозрение.
        Map<String, Object> details = score.checks().stream()
                .filter(check -> check.checkId().equals(CheckIds.BURST))
                .filter(check -> !Boolean.TRUE.equals(check.details().get("mocked")))
                .map(CheckContribution::details)
                .filter(value -> value.containsKey("window_start"))
                .findFirst().orElseGet(() -> new BurstCheck(burstProperties).evaluate(snapshot).details());
        if (!(details.get("max_burst_10m") instanceof Number stars)
                || !(details.get("ratio") instanceof Number ratio)
                || stars.longValue() < burstProperties.minBurst()
                || ratio.doubleValue() <= burstProperties.ratioFloor()) {
            return List.of();
        }
        Instant from = Instant.parse(details.get("window_start").toString());
        Instant to = from.plus(Duration.ofMinutes(Math.min(10, minutes.length)));
        if (to.isAfter(snapshot.window().to())) to = snapshot.window().to();
        int start = (int) Duration.between(snapshot.window().from(), from).toMinutes();
        int end = (int) Duration.between(snapshot.window().from(), to).toMinutes();
        int peak = 0;
        for (int i = Math.max(0, start); i < Math.min(minutes.length, end); i++) {
            peak = Math.max(peak, minutes[i]);
        }
        return List.of(new StarTimeline.SpikeInterval(from, to, stars.longValue(), ratio.doubleValue(), peak,
                SuspiciousActorsService.isRealAndActive(score, CheckIds.BURST)));
    }
}
