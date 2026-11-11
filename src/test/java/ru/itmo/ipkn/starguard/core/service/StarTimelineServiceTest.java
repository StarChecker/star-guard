package ru.itmo.ipkn.starguard.core.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import ru.itmo.ipkn.starguard.core.config.ActorOverlapProperties;
import ru.itmo.ipkn.starguard.core.config.ApiProperties;
import ru.itmo.ipkn.starguard.core.config.BurstProperties;
import ru.itmo.ipkn.starguard.core.scoring.CheckMode;
import ru.itmo.ipkn.starguard.core.domain.DataAvailability;
import ru.itmo.ipkn.starguard.core.domain.EventCounts;
import ru.itmo.ipkn.starguard.core.domain.RepoRef;
import ru.itmo.ipkn.starguard.core.domain.RepoSnapshot;
import ru.itmo.ipkn.starguard.core.domain.StarIndex;
import ru.itmo.ipkn.starguard.core.domain.StarMark;
import ru.itmo.ipkn.starguard.core.domain.StarTimeline;
import ru.itmo.ipkn.starguard.core.domain.TimeWindow;
import ru.itmo.ipkn.starguard.core.scoring.ActorOverlapCheck;
import ru.itmo.ipkn.starguard.core.scoring.CheckContribution;
import ru.itmo.ipkn.starguard.core.scoring.CheckIds;
import ru.itmo.ipkn.starguard.core.scoring.FreshAccountsCheck;
import ru.itmo.ipkn.starguard.core.scoring.RepoScore;
import ru.itmo.ipkn.starguard.core.scoring.ScoreLevel;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class StarTimelineServiceTest {

    private static final Instant FROM = Instant.parse("2026-11-10T00:00:00Z");
    private static final TimeWindow WINDOW = new TimeWindow(FROM, FROM.plus(Duration.ofHours(2)), 2);

    @Test
    void marksFarmStarsExactlyWithMinuteResolution() {
        // 10 ботов (1..10) ставят звёзды цели и двум репозиториям фермы в 00:15; 5 живых (101..105) — в 01:05
        StarIndex index = farmIndex();
        List<StarMark> marks = new ArrayList<>();
        int[] perMinute = new int[120];
        for (long bot = 1; bot <= 10; bot++) {
            marks.add(new StarMark(FROM.plus(Duration.ofMinutes(15)), bot, false));
            perMinute[15]++;
        }
        for (long human = 101; human <= 105; human++) {
            marks.add(new StarMark(FROM.plus(Duration.ofMinutes(65)), human, false));
            perMinute[65]++;
        }

        StarTimeline timeline = service().build(snapshot(index, perMinute), score(true, false), marks);

        assertThat(timeline.bucketMinutes()).isEqualTo(10);
        assertThat(timeline.points()).hasSize(12);
        assertThat(timeline.estimated()).isFalse();
        assertThat(timeline.points().get(1)).isEqualTo(new StarTimeline.Point(FROM.plus(Duration.ofMinutes(10)), 10, 10));
        assertThat(timeline.points().get(6)).isEqualTo(new StarTimeline.Point(FROM.plus(Duration.ofMinutes(60)), 5, 0));
        assertThat(timeline.suspiciousStars()).isEqualTo(10);
        assertThat(timeline.suspiciousByCheck()).containsExactlyEntriesOf(Map.of(CheckIds.ACTOR_OVERLAP, 10));
    }

    @Test
    void distributesHourResolutionMarksProportionally() {
        StarIndex index = farmIndex();
        List<StarMark> marks = new ArrayList<>();
        int[] perMinute = new int[120];
        // первый час: 10 ботов + 10 живых, звёзды поровну в минутах 5 и 45
        for (long bot = 1; bot <= 10; bot++) {
            marks.add(new StarMark(FROM, bot, true));
        }
        for (long human = 201; human <= 210; human++) {
            marks.add(new StarMark(FROM, human, true));
        }
        perMinute[5] = 10;
        perMinute[45] = 10;

        StarTimeline timeline = service().build(snapshot(index, perMinute), score(true, false), marks);

        assertThat(timeline.estimated()).isTrue();
        assertThat(timeline.points().get(0).suspicious()).isEqualTo(5);
        assertThat(timeline.points().get(4).suspicious()).isEqualTo(5);
        assertThat(timeline.suspiciousStars()).isEqualTo(10);
    }

    @Test
    void mockedOrInactiveChecksDoNotMarkStars() {
        StarIndex index = farmIndex();
        List<StarMark> marks = List.of(new StarMark(FROM.plus(Duration.ofMinutes(15)), 1, false));
        int[] perMinute = new int[120];
        perMinute[15] = 1;

        assertThat(service().build(snapshot(index, perMinute), score(true, true), marks).suspiciousStars()).isZero();
        assertThat(service().build(snapshot(index, perMinute), score(false, false), marks).suspiciousStars()).isZero();
    }

    @Test
    void checkWithZeroScoreDoesNotMarkSingleMatches() {
        StarIndex index = farmIndex();
        List<StarMark> marks = List.of(new StarMark(FROM.plus(Duration.ofMinutes(15)), 1, false));
        int[] perMinute = new int[120];
        perMinute[15] = 1;
        RepoScore nothingFound = new RepoScore(3, ScoreLevel.LOW, 0.8, false, List.of(new CheckContribution(
                CheckIds.ACTOR_OVERLAP, 0.0, 0.8, 0.22, 0.0, true, null, Map.of("mocked", false))),
                false, List.of(), Instant.EPOCH);

        assertThat(service().build(snapshot(index, perMinute), nothingFound, marks).suspiciousStars()).isZero();
    }

    @Test
    void bucketGrowsWithWindow() {
        assertThat(StarTimelineService.bucketMinutes(6)).isEqualTo(10);
        assertThat(StarTimelineService.bucketMinutes(12)).isEqualTo(20);
        assertThat(StarTimelineService.bucketMinutes(24)).isEqualTo(30);
        assertThat(StarTimelineService.bucketMinutes(30 * 24)).isEqualTo(720);
    }

    @Test
    void spikeKeepsExactSlidingWindowAcrossHourBoundary() {
        int[] minutes = new int[120];
        minutes[58] = 8;
        minutes[60] = 12;
        RepoSnapshot snapshot = snapshot(StarIndex.empty(), minutes);
        var burst = new ru.itmo.ipkn.starguard.core.scoring.BurstCheck(
                new BurstProperties(CheckMode.REAL, true, 3.0, 12.0, 10, 60, .3)).evaluate(snapshot);
        var score = new RepoScore(70, ScoreLevel.HIGH, 1, false, List.of(new CheckContribution(
                CheckIds.BURST, burst.score(), burst.confidence(), .3, .3, true, null, burst.details())),
                false, List.of(), WINDOW.to());
        var interval = service().build(snapshot, score, List.of()).spikeIntervals().getFirst();
        assertThat(interval.from()).isEqualTo(FROM.plusSeconds(51 * 60));
        assertThat(interval.to()).isEqualTo(FROM.plusSeconds(61 * 60));
        assertThat(interval.stars()).isEqualTo(20);
        assertThat(interval.ratio()).isEqualTo(20);
        assertThat(interval.peak()).isEqualTo(12);
        assertThat(interval.suspicious()).isTrue();
    }

    @Test
    void mockAndInactiveBurstOnlyProduceNeutralIntervals() {
        int[] minutes = new int[120];
        minutes[119] = 20;
        for (boolean mocked : List.of(true, false)) {
            var score = new RepoScore(70, ScoreLevel.HIGH, 1, false, List.of(new CheckContribution(
                    CheckIds.BURST, 1, 1, .3, .3, mocked, null, Map.of("mocked", mocked))),
                    mocked, List.of(), WINDOW.to());
            var interval = service().build(snapshot(StarIndex.empty(), minutes), score, List.of())
                    .spikeIntervals().getFirst();
            assertThat(interval.suspicious()).isFalse();
            assertThat(interval.from()).isEqualTo(FROM.plusSeconds(110 * 60));
            assertThat(interval.to()).isEqualTo(WINDOW.to());
        }
    }

    @Test
    void emptySmallAndSteadySeriesHaveNoSpike() {
        int[] small = new int[120];
        small[0] = 9;
        int[] steady = new int[120];
        java.util.Arrays.fill(steady, 2);
        for (int[] minutes : List.of(new int[120], small, steady)) {
            assertThat(service().build(snapshot(StarIndex.empty(), minutes), score(true, true), List.of())
                    .spikeIntervals()).isEmpty();
        }
        assertThat(service().build(snapshot(StarIndex.empty(), null), score(true, true), List.of())
                .spikeIntervals()).isEmpty();
    }

    @Test
    void hourEstimatesPreserveRareStarsAndWorkForMultiHourBuckets() {
        var window = new TimeWindow(FROM, FROM.plusSeconds(96 * 3600), 96);
        int[] minutes = new int[96 * 60];
        minutes[0] = 1;
        minutes[20] = 1;
        minutes[40] = 1;
        var snapshot = new RepoSnapshot(new RepoRef("demo", "target"), window, window.to(), EventCounts.empty(),
                3, 3, minutes, DataAvailability.pocDefaults(), farmIndex());
        var marks = List.of(new StarMark(FROM, 1, true), new StarMark(FROM, 201, true),
                new StarMark(FROM, 202, true));
        var timeline = service().build(snapshot, score(true, false), marks);
        assertThat(timeline.bucketMinutes()).isEqualTo(180);
        assertThat(timeline.points().getFirst().suspicious()).isEqualTo(1);
        assertThat(timeline.points().stream().mapToInt(StarTimeline.Point::suspicious).sum()).isEqualTo(1);
    }

    private static StarTimelineService service() {
        DefaultListableBeanFactory factory = new DefaultListableBeanFactory();
        factory.registerSingleton("actorOverlap", new ActorOverlapCheck(ActorOverlapProperties.defaults()));
        return new StarTimelineService(new SuspiciousActorsService(
                factory.getBeanProvider(ActorOverlapCheck.class),
                factory.getBeanProvider(FreshAccountsCheck.class), new ApiProperties(null, null)),
                new BurstProperties(CheckMode.REAL, true, 3.0, 12.0, 10, 60, 0.3));
    }

    private static StarIndex farmIndex() {
        StarIndex.Builder builder = new StarIndex.Builder(1000);
        for (long bot = 1; bot <= 10; bot++) {
            builder.add("demo/target", bot);
            builder.add("farm/a", bot);
            builder.add("farm/b", bot);
        }
        for (long human = 101; human <= 105; human++) {
            builder.add("demo/target", human);
        }
        for (long human = 201; human <= 210; human++) {
            builder.add("demo/target", human);
        }
        return builder.build(2);
    }

    private static RepoSnapshot snapshot(StarIndex index, int[] perMinute) {
        return new RepoSnapshot(new RepoRef("demo", "target"), WINDOW, WINDOW.to(), EventCounts.empty(),
                0, 0, perMinute, DataAvailability.pocDefaults().withCrossRepoOverlap(true), index);
    }

    private static RepoScore score(boolean active, boolean mocked) {
        return new RepoScore(50, ScoreLevel.MEDIUM, 0.8, false, List.of(new CheckContribution(
                CheckIds.ACTOR_OVERLAP, 1.0, 0.8, 0.22, 0.2, active, null, Map.of("mocked", mocked))),
                false, List.of(), Instant.EPOCH);
    }
}
