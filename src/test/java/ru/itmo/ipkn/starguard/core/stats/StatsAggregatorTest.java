package ru.itmo.ipkn.starguard.core.stats;

import org.junit.jupiter.api.Test;
import ru.itmo.ipkn.starguard.core.domain.EventCounts;
import ru.itmo.ipkn.starguard.core.domain.EventType;
import ru.itmo.ipkn.starguard.core.domain.GhEvent;
import ru.itmo.ipkn.starguard.core.domain.RepoRef;
import ru.itmo.ipkn.starguard.core.domain.RepoStats;
import ru.itmo.ipkn.starguard.core.domain.TimeWindow;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class StatsAggregatorTest {

    private static final Instant FROM = Instant.parse("2026-11-10T00:00:00Z");
    private static final TimeWindow WINDOW = new TimeWindow(FROM, Instant.parse("2026-11-10T03:00:00Z"), 3);
    private static final RepoRef REPO = new RepoRef("demo", "repo");
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-10T04:00:00Z"), ZoneOffset.UTC);

    private final StatsAggregator aggregator = new StatsAggregator(CLOCK);

    @Test
    void countsTypesActorsAndDeduplicatesEvents() {
        List<GhEvent> events = new ArrayList<>(List.of(
                event("1", EventType.WATCH, 1, 1),
                event("2", EventType.WATCH, 2, 2),
                event("3", EventType.FORK, 1, 3),
                event("4", EventType.PUSH, 3, 4),
                event("5", EventType.ISSUES, 4, 5),
                event("6", EventType.PULL_REQUEST, 5, 6),
                event("7", EventType.OTHER, 6, 7),
                // дубликат eventId не должен попасть в статистику
                event("1", EventType.WATCH, 1, 8),
                // событие вне окна
                new GhEvent("8", EventType.WATCH, 9, REPO.fullName(), 9, "dev9",
                        WINDOW.to().plusSeconds(60)),
                // событие другого репозитория
                new GhEvent("9", EventType.WATCH, 10, "other/repo", 10, "dev10",
                        FROM.plusSeconds(60))));

        RepoAggregate aggregate = aggregator.aggregate(REPO, WINDOW, events);
        RepoStats stats = aggregate.stats();
        EventCounts counts = stats.counts();

        assertThat(counts.total()).isEqualTo(7);
        assertThat(counts.stars()).isEqualTo(2);
        assertThat(counts.forks()).isEqualTo(1);
        assertThat(counts.pushes()).isEqualTo(1);
        assertThat(counts.issues()).isEqualTo(1);
        assertThat(counts.pullRequests()).isEqualTo(1);
        assertThat(counts.other()).isEqualTo(1);
        assertThat(stats.uniqueActors()).isEqualTo(6);
        assertThat(stats.starActors()).isEqualTo(2);
        assertThat(stats.starsByHour()).hasSize(3);
        assertThat(stats.starsByHour().get(0).stars()).isEqualTo(2);
        assertThat(stats.starsByHour().get(1).stars()).isZero();
    }

    @Test
    void computesPeakStarsInTenMinuteWindow() {
        List<GhEvent> events = new ArrayList<>();
        for (int minute = 10; minute < 15; minute++) {
            events.add(event("star-" + minute, EventType.WATCH, 100 + minute, minute));
        }
        events.add(event("star-late", EventType.WATCH, 200, 150));

        RepoStats stats = aggregator.aggregate(REPO, WINDOW, events).stats();

        assertThat(stats.peakStars10m()).isEqualTo(5);
    }

    @Test
    void snapshotContainsMinuteSeriesAndDataAvailabilityFlags() {
        RepoAggregate aggregate = aggregator.aggregate(REPO, WINDOW,
                List.of(event("1", EventType.WATCH, 1, 1)));

        assertThat(aggregate.snapshot().starsByMinute()).hasSize(180);
        assertThat(aggregate.snapshot().starsByMinute()[1]).isEqualTo(1);
        assertThat(aggregate.snapshot().data().actorHistory()).isFalse();
        assertThat(aggregate.snapshot().data().crossRepoOverlap()).isFalse();
        assertThat(aggregate.snapshot().data().globalHourProfile()).isFalse();
    }

    @Test
    void maxSlidingSumHandlesEmptyAndShortSeries() {
        assertThat(StatsAggregator.maxSlidingSum(new int[0], 10)).isZero();
        assertThat(StatsAggregator.maxSlidingSum(new int[]{1, 2, 3}, 10)).isEqualTo(6);
        assertThat(StatsAggregator.maxSlidingSum(new int[]{0, 5, 0, 5, 0}, 2)).isEqualTo(5);
    }

    private static GhEvent event(String id, EventType type, long actorId, int minute) {
        return new GhEvent(id, type, 1L, REPO.fullName(), actorId, "dev" + actorId,
                FROM.plusSeconds(minute * 60L));
    }
}
