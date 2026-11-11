package ru.itmo.ipkn.starguard.core.index;

import org.junit.jupiter.api.Test;
import ru.itmo.ipkn.starguard.core.domain.ActorActivity;
import ru.itmo.ipkn.starguard.core.domain.ActorProfile;
import ru.itmo.ipkn.starguard.core.domain.EventCounts;
import ru.itmo.ipkn.starguard.core.domain.RepoRef;
import ru.itmo.ipkn.starguard.core.domain.TimeWindow;
import ru.itmo.ipkn.starguard.core.stats.RepoAggregate;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RepoStatsComposerTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-10T12:00:00Z"), ZoneOffset.UTC);
    private static final RepoRef REPO = new RepoRef("demo", "star-guard");
    private static final Instant H07 = Instant.parse("2026-11-10T07:00:00Z");
    private static final Instant H08 = Instant.parse("2026-11-10T08:00:00Z");
    private static final Instant H09 = Instant.parse("2026-11-10T09:00:00Z");
    private static final TimeWindow WINDOW_2H = new TimeWindow(
            Instant.parse("2026-11-10T08:00:00Z"),
            Instant.parse("2026-11-10T10:00:00Z"),
            2);

    @Test
    void composesTailHoursIntoOneReport() {
        RepoHourAggregate hour07 = aggregate(H07, 1, 1, new int[60], 1, 1);
        int[] minutes08 = new int[60];
        minutes08[59] = 5;
        RepoHourAggregate hour08 = aggregate(H08, 5, 2, minutes08, 3, 2);
        int[] minutes09 = new int[60];
        minutes09[0] = 5;
        RepoHourAggregate hour09 = aggregate(H09, 5, 2, minutes09, 4, 2);

        RepoAggregate aggregate = RepoStatsComposer.compose(
                REPO, WINDOW_2H, List.of(hour07, hour08, hour09), CLOCK);

        assertThat(aggregate.stats().counts().stars()).isEqualTo(10);
        assertThat(aggregate.stats().counts().pushes()).isEqualTo(4);
        assertThat(aggregate.stats().uniqueActors()).isEqualTo(7);
        assertThat(aggregate.stats().contributors()).isNull();
        assertThat(aggregate.stats().starActors()).isEqualTo(4);
        assertThat(aggregate.stats().starsByHour()).hasSize(2);
        assertThat(aggregate.stats().peakStars10m()).isEqualTo(10);
        assertThat(aggregate.snapshot().starsByMinute()).hasSize(120);
        assertThat(aggregate.snapshot().starsByMinute()[59]).isEqualTo(5);
        assertThat(aggregate.snapshot().starsByMinute()[60]).isEqualTo(5);
        assertThat(aggregate.snapshot().window()).isEqualTo(WINDOW_2H);
    }

    @Test
    void buildsStarActorProfilesFromHourlyActivityOfSubWindow() {
        Instant createdAt = Instant.parse("2026-11-01T00:00:00Z");
        RepoHourAggregate hour07 = withActors(H07,
                new ActorActivity(9, "early", H07, null, null, true));
        RepoHourAggregate hour08 = withActors(H08,
                new ActorActivity(1, "alice", H08.plusSeconds(600), H08.plusSeconds(60), null, false),
                new ActorActivity(2, "bob", H08.plusSeconds(120), null, createdAt, true));
        RepoHourAggregate hour09 = withActors(H09,
                new ActorActivity(1, "alice", H09.plusSeconds(60), null, null, true),
                new ActorActivity(3, "carol", H09.plusSeconds(300), H09.plusSeconds(300), null, false));

        RepoAggregate aggregate = RepoStatsComposer.compose(
                REPO, WINDOW_2H, List.of(hour07, hour08, hour09), CLOCK);

        assertThat(aggregate.starActors()).extracting(ActorActivity::login)
                .containsExactly("alice", "bob");
        ActorActivity alice = aggregate.starActors().getFirst();
        assertThat(alice.firstSeen()).isEqualTo(H08.plusSeconds(600));
        assertThat(alice.lastActivityAt()).isEqualTo(H08.plusSeconds(60));
        assertThat(aggregate.snapshot().starActorProfiles())
                .extracting(ActorProfile::actorHash)
                .containsExactlyInAnyOrder(ActorProfile.hashOf(1), ActorProfile.hashOf(2));
        assertThat(aggregate.snapshot().starActorProfiles())
                .filteredOn(profile -> profile.actorHash().equals(ActorProfile.hashOf(2)))
                .extracting(ActorProfile::accountCreatedAt)
                .containsExactly(createdAt);
    }

    @Test
    void deduplicatesContributorsAndActorsAcrossHoursOfSelectedWindow() {
        var before = withActors(H07, new ActorActivity(9, "before", H07, H07, null, false));
        var first = withActors(H08,
                new ActorActivity(1, "dev", H08, H08, null, true),
                new ActorActivity(2, "watcher", H08, null, null, true));
        var second = withActors(H09,
                new ActorActivity(1, "dev", H09, H09, null, true),
                new ActorActivity(3, "other", H09, H09, null, false, false));
        var aggregate = RepoStatsComposer.compose(REPO, WINDOW_2H, List.of(before, first, second), CLOCK);
        assertThat(aggregate.stats().contributors()).isEqualTo(1);
        assertThat(aggregate.stats().uniqueActors()).isEqualTo(3);
        assertThat(aggregate.stats().starActors()).isEqualTo(2);
        assertThat(aggregate.snapshot().starActors()).isEqualTo(2);
    }

    private static RepoHourAggregate withActors(Instant hour, ActorActivity... actors) {
        return new RepoHourAggregate(hour, EventCounts.empty(), new int[60], 0, 0, List.of(actors));
    }

    private static RepoHourAggregate aggregate(
            Instant hour, int stars, int pushes, int[] minutes, int actors, int starActors) {
        return new RepoHourAggregate(
                hour,
                new EventCounts(stars + pushes, stars, 0, pushes, 0, 0, 0),
                minutes,
                actors,
                starActors);
    }
}
