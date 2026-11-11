package ru.itmo.ipkn.starguard.core.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import ru.itmo.ipkn.starguard.core.config.ActorOverlapProperties;
import ru.itmo.ipkn.starguard.core.config.ApiProperties;
import ru.itmo.ipkn.starguard.core.config.FreshAccountsProperties;
import ru.itmo.ipkn.starguard.core.domain.ActorActivity;
import ru.itmo.ipkn.starguard.core.domain.DataAvailability;
import ru.itmo.ipkn.starguard.core.domain.EventCounts;
import ru.itmo.ipkn.starguard.core.domain.RepoRef;
import ru.itmo.ipkn.starguard.core.domain.RepoSnapshot;
import ru.itmo.ipkn.starguard.core.domain.StarIndex;
import ru.itmo.ipkn.starguard.core.domain.StarMark;
import ru.itmo.ipkn.starguard.core.domain.SuspiciousActor;
import ru.itmo.ipkn.starguard.core.domain.TimeWindow;
import ru.itmo.ipkn.starguard.core.scoring.ActorOverlapCheck;
import ru.itmo.ipkn.starguard.core.scoring.CheckContribution;
import ru.itmo.ipkn.starguard.core.scoring.CheckIds;
import ru.itmo.ipkn.starguard.core.scoring.FreshAccountsCheck;
import ru.itmo.ipkn.starguard.core.scoring.RepoScore;
import ru.itmo.ipkn.starguard.core.scoring.ScoreLevel;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SuspiciousActorsServiceTest {
    private static final Instant FROM = Instant.parse("2026-11-10T00:00:00Z");
    private static final TimeWindow WINDOW = new TimeWindow(FROM, FROM.plusSeconds(7200), 2);
    private static final List<ActorActivity> ACTORS = List.of(
            new ActorActivity(1, "fresh-dev", FROM.plusSeconds(120), null, FROM.minusSeconds(3600), true),
            new ActorActivity(2, "old-dev", FROM.plusSeconds(60), null, FROM.minusSeconds(86400L * 365), true),
            new ActorActivity(3, "", FROM.plusSeconds(240), null, null, true));

    @Test
    void deduplicatesAndRanksMultipleReasonsBeforeOverlapOnly() {
        var service = service(new ApiProperties(null, 2));
        var marks = marks();
        var snapshot = snapshot();
        var flags = service.flags(snapshot, score(true, false, 1), marks);
        var actors = new ArrayList<>(ACTORS);
        actors.add(new ActorActivity(1, "fresh-dev", FROM.plusSeconds(3600), null, null, true));
        var sample = service.sample(snapshot, actors, marks, flags);

        assertThat(flags).hasSize(3);
        assertThat(sample).hasSize(2);
        assertThat(sample.getFirst().login()).isEqualTo("fresh-dev");
        assertThat(sample.getFirst().reasons()).containsExactly("fresh", "overlap");
        assertThat(sample.getFirst().firstSeen()).isEqualTo(FROM.plusSeconds(120));
        assertThat(sample.getFirst().accountCreatedAt()).isEqualTo(FROM.minusSeconds(3600));
        assertThat(sample.getFirst().sharedRepos()).isEqualTo(2);
        assertThat(sample).extracting(SuspiciousActor::actorHash).doesNotHaveDuplicates();
        assertThat(service.sample(snapshot, actors.reversed(), marks.reversed(), flags)).isEqualTo(sample);
    }

    @Test
    void hidesLoginsAndFallsBackToHashesWithoutProfiles() {
        var hidden = service(new ApiProperties(ApiProperties.ActorLogins.HASH, 50));
        var flags = hidden.flags(snapshot(), score(true, false, 1), marks());
        assertThat(hidden.sample(snapshot(), ACTORS, marks(), flags))
                .allSatisfy(actor -> assertThat(actor.login()).isNull());

        var shown = service(new ApiProperties(null, null));
        var compact = shown.sample(snapshot(), List.of(), marks(), flags);
        assertThat(compact).hasSize(3).allSatisfy(actor -> {
            assertThat(actor.login()).isNull();
            assertThat(actor.accountCreatedAt()).isNull();
            assertThat(actor.actorHash()).matches("[0-9a-f]{12}");
            assertThat(actor.firstSeen()).isEqualTo(FROM.plusSeconds(60));
            assertThat(actor.sharedRepos()).isEqualTo(2);
        });
    }

    @Test
    void inactiveMockedAndZeroScoreChecksDoNotAccuseActors() {
        var service = service(new ApiProperties(null, null));
        assertThat(service.flags(snapshot(), score(false, false, 1), marks())).isEmpty();
        assertThat(service.flags(snapshot(), score(true, true, 1), marks())).isEmpty();
        assertThat(service.flags(snapshot(), score(true, false, 0), marks())).isEmpty();
    }

    @Test
    void freshActorsRemainVisibleWhenStarMarksAreUnavailable() {
        var service = service(new ApiProperties(null, null));
        var flags = service.flags(snapshot(), score(true, false, 1), List.of(), ACTORS);
        assertThat(flags.get(1L)).contains(CheckIds.FRESH_ACCOUNTS);
        var sample = service.sample(snapshot(), ACTORS, List.of(), flags);
        assertThat(sample.getFirst().login()).isEqualTo("fresh-dev");
        assertThat(sample.getFirst().firstSeen()).isEqualTo(FROM.plusSeconds(120));
    }

    @Test
    void unknownAccountAgeDoesNotImplyFreshness() {
        var service = service(new ApiProperties(null, null));
        var flags = service.flags(snapshot(), score(true, false, 1), marks());
        assertThat(flags.get(3L)).containsExactly(CheckIds.ACTOR_OVERLAP);
    }

    @Test
    void validatesBoundedSampleSize() {
        assertThat(new ApiProperties(null, null).suspiciousActorsLimit()).isEqualTo(50);
        assertThatThrownBy(() -> new ApiProperties(null, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ApiProperties(null, 501)).isInstanceOf(IllegalArgumentException.class);
    }

    private static SuspiciousActorsService service(ApiProperties properties) {
        var factory = new DefaultListableBeanFactory();
        factory.registerSingleton("overlap", new ActorOverlapCheck(ActorOverlapProperties.defaults()));
        factory.registerSingleton("fresh", new FreshAccountsCheck(FreshAccountsProperties.defaults()));
        return new SuspiciousActorsService(factory.getBeanProvider(ActorOverlapCheck.class),
                factory.getBeanProvider(FreshAccountsCheck.class), properties);
    }

    private static List<StarMark> marks() {
        return List.of(new StarMark(FROM.plusSeconds(60), 1, false),
                new StarMark(FROM.plusSeconds(3600), 1, false),
                new StarMark(FROM.plusSeconds(60), 2, false),
                new StarMark(FROM.plusSeconds(60), 3, false));
    }

    private static RepoSnapshot snapshot() {
        var builder = new StarIndex.Builder(100);
        for (long id = 1; id <= 3; id++) {
            builder.add("demo/target", id);
            builder.add("farm/a", id);
            builder.add("farm/b", id);
        }
        return new RepoSnapshot(new RepoRef("demo", "target"), WINDOW, WINDOW.to(), EventCounts.empty(),
                3, 3, new int[120], DataAvailability.pocDefaults(), ActorActivity.profiles(ACTORS), builder.build(2));
    }

    private static RepoScore score(boolean active, boolean mocked, double value) {
        return new RepoScore(50, ScoreLevel.MEDIUM, .8, false,
                List.of(CheckIds.FRESH_ACCOUNTS, CheckIds.ACTOR_OVERLAP).stream().map(id ->
                        new CheckContribution(id, value, .8, .2, .2, active, null, Map.of("mocked", mocked))).toList(),
                mocked, List.of(), WINDOW.to());
    }
}
