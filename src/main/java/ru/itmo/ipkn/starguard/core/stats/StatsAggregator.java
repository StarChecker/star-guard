package ru.itmo.ipkn.starguard.core.stats;

import org.springframework.stereotype.Component;
import ru.itmo.ipkn.starguard.core.domain.ActorActivity;
import ru.itmo.ipkn.starguard.core.domain.DataAvailability;
import ru.itmo.ipkn.starguard.core.domain.EventCounts;
import ru.itmo.ipkn.starguard.core.domain.GhEvent;
import ru.itmo.ipkn.starguard.core.domain.HourBucket;
import ru.itmo.ipkn.starguard.core.domain.RepoRef;
import ru.itmo.ipkn.starguard.core.domain.RepoSnapshot;
import ru.itmo.ipkn.starguard.core.domain.RepoStats;
import ru.itmo.ipkn.starguard.core.domain.StarIndex;
import ru.itmo.ipkn.starguard.core.domain.TimeWindow;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Считает статистику репозитория за окно и собирает snapshot для scoring. */
@Component
public class StatsAggregator {

    private static final int BURST_WINDOW_MINUTES = 10;

    private final Clock clock;

    public StatsAggregator(Clock clock) {
        this.clock = clock;
    }

    public RepoAggregate aggregate(RepoRef repo, TimeWindow window, List<GhEvent> events) {
        return aggregate(repo, window, events, StarIndex.empty());
    }

    /**
     * @param starIndex глобальный индекс звёзд окна для C4; не влияет на статистику репозитория
     */
    public RepoAggregate aggregate(
            RepoRef repo, TimeWindow window, List<GhEvent> events, StarIndex starIndex) {
        StarIndex index = starIndex == null ? StarIndex.empty() : starIndex;
        int stars = 0;
        int forks = 0;
        int pushes = 0;
        int issues = 0;
        int pullRequests = 0;
        int other = 0;
        int total = 0;

        Set<Long> actors = new HashSet<>();
        Set<Long> starActors = new HashSet<>();
        Set<String> seenEventIds = new HashSet<>();
        Map<Long, ActorActivity> activities = new HashMap<>();
        int[] starsByMinute = new int[window.hours() * 60];

        for (GhEvent event : events) {
            if (!event.repoFullName().equalsIgnoreCase(repo.fullName())) {
                continue;
            }
            if (event.createdAt().isBefore(window.from())
                    || !event.createdAt().isBefore(window.to())) {
                continue;
            }
            if (!seenEventIds.add(event.eventId())) {
                continue;
            }

            total++;
            actors.add(event.actorId());
            activities.merge(event.actorId(), ActorActivity.of(event), ActorActivity::merge);
            switch (event.type()) {
                case WATCH -> {
                    stars++;
                    starActors.add(event.actorId());
                    int minute = (int) Duration.between(window.from(), event.createdAt()).toMinutes();
                    if (minute >= 0 && minute < starsByMinute.length) {
                        starsByMinute[minute]++;
                    }
                }
                case FORK -> forks++;
                case PUSH -> pushes++;
                case ISSUES -> issues++;
                case PULL_REQUEST -> pullRequests++;
                case OTHER -> other++;
            }
        }

        EventCounts counts = new EventCounts(total, stars, forks, pushes, issues, pullRequests, other);
        List<HourBucket> starsByHour = buildHourBuckets(window, starsByMinute);
        int peakStars10m = maxSlidingSum(starsByMinute, BURST_WINDOW_MINUTES);

        RepoStats stats = new RepoStats(
                repo, window, counts, actors.size(), starActors.size(), peakStars10m, starsByHour,
                (int) activities.values().stream().filter(ActorActivity::contributor).count());

        List<ActorActivity> starActivities = ActorActivity.starredSorted(activities.values());
        RepoSnapshot snapshot = new RepoSnapshot(
                repo,
                window,
                clock.instant(),
                counts,
                actors.size(),
                starActors.size(),
                starsByMinute,
                DataAvailability.pocDefaults().withCrossRepoOverlap(index.available()),
                ActorActivity.profiles(starActivities),
                index);

        return new RepoAggregate(stats, snapshot, starActivities);
    }

    private static List<HourBucket> buildHourBuckets(TimeWindow window, int[] starsByMinute) {
        List<HourBucket> buckets = new ArrayList<>(window.hours());
        for (int hour = 0; hour < window.hours(); hour++) {
            int sum = 0;
            int from = hour * 60;
            int to = Math.min(from + 60, starsByMinute.length);
            for (int minute = from; minute < to; minute++) {
                sum += starsByMinute[minute];
            }
            buckets.add(new HourBucket(window.from().plus(Duration.ofHours(hour)), sum));
        }
        return List.copyOf(buckets);
    }

    public static int maxSlidingSum(int[] values, int windowSize) {
        if (values.length == 0 || windowSize <= 0) {
            return 0;
        }
        int size = Math.min(windowSize, values.length);
        int sum = 0;
        for (int i = 0; i < size; i++) {
            sum += values[i];
        }
        int max = sum;
        for (int i = size; i < values.length; i++) {
            sum += values[i] - values[i - size];
            max = Math.max(max, sum);
        }
        return max;
    }
}
