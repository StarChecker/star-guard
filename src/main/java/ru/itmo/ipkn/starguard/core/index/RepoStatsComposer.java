package ru.itmo.ipkn.starguard.core.index;

import ru.itmo.ipkn.starguard.core.domain.DataAvailability;
import ru.itmo.ipkn.starguard.core.domain.EventCounts;
import ru.itmo.ipkn.starguard.core.domain.HourBucket;
import ru.itmo.ipkn.starguard.core.domain.RepoRef;
import ru.itmo.ipkn.starguard.core.domain.RepoSnapshot;
import ru.itmo.ipkn.starguard.core.domain.RepoStats;
import ru.itmo.ipkn.starguard.core.domain.TimeWindow;
import ru.itmo.ipkn.starguard.core.stats.RepoAggregate;
import ru.itmo.ipkn.starguard.core.stats.StatsAggregator;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;

/**
 * Собирает отчёт за запрошенное подокно из почасовых агрегатов индекса.
 * uniqueActors/starActors суммируются по часам (верхняя граница, без дедупликации
 * между часами) — этого достаточно для PoC, где проверки замоканы.
 */
public final class RepoStatsComposer {

    private static final int BURST_WINDOW_MINUTES = 10;

    private RepoStatsComposer() {
    }

    public static RepoAggregate compose(
            RepoRef repo,
            TimeWindow window,
            List<RepoHourAggregate> allHours,
            Clock clock) {
        int hours = window.hours();
        if (allHours.size() < hours) {
            throw new IllegalArgumentException("Not enough hourly aggregates for window");
        }
        List<RepoHourAggregate> selected = allHours.subList(allHours.size() - hours, allHours.size());

        int total = 0;
        int stars = 0;
        int forks = 0;
        int pushes = 0;
        int issues = 0;
        int pullRequests = 0;
        int other = 0;
        int uniqueActors = 0;
        int starActors = 0;
        int[] starsByMinute = new int[hours * 60];
        List<HourBucket> hourBuckets = new ArrayList<>(hours);

        for (int i = 0; i < hours; i++) {
            RepoHourAggregate hour = selected.get(i);
            EventCounts counts = hour.counts();
            total += counts.total();
            stars += counts.stars();
            forks += counts.forks();
            pushes += counts.pushes();
            issues += counts.issues();
            pullRequests += counts.pullRequests();
            other += counts.other();
            uniqueActors += hour.uniqueActors();
            starActors += hour.starActors();
            System.arraycopy(hour.starsByMinute(), 0, starsByMinute, i * 60, 60);
            hourBuckets.add(new HourBucket(hour.hour(), counts.stars()));
        }

        EventCounts counts = new EventCounts(
                total, stars, forks, pushes, issues, pullRequests, other);
        int peakStars10m = StatsAggregator.maxSlidingSum(starsByMinute, BURST_WINDOW_MINUTES);
        RepoStats stats = new RepoStats(
                repo, window, counts, uniqueActors, starActors, peakStars10m, hourBuckets);
        RepoSnapshot snapshot = new RepoSnapshot(
                repo,
                window,
                clock.instant(),
                counts,
                uniqueActors,
                starActors,
                starsByMinute,
                DataAvailability.pocDefaults());
        return new RepoAggregate(stats, snapshot);
    }
}
