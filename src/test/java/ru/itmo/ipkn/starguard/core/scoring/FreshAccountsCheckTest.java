package ru.itmo.ipkn.starguard.core.scoring;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import ru.itmo.ipkn.starguard.core.config.FreshAccountsProperties;
import ru.itmo.ipkn.starguard.core.domain.ActorProfile;
import ru.itmo.ipkn.starguard.core.domain.DataAvailability;
import ru.itmo.ipkn.starguard.core.domain.EventCounts;
import ru.itmo.ipkn.starguard.core.domain.RepoRef;
import ru.itmo.ipkn.starguard.core.domain.RepoSnapshot;
import ru.itmo.ipkn.starguard.core.domain.TimeWindow;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

class FreshAccountsCheckTest {

    private static final TimeWindow WINDOW = new TimeWindow(
            Instant.parse("2026-11-10T00:00:00Z"), Instant.parse("2026-11-10T03:00:00Z"), 3);
    private static final Instant STARRED_AT = WINDOW.from().plusSeconds(600);
    private static final DataAvailability WITH_HISTORY = new DataAvailability(true, false, false);

    private final FreshAccountsCheck check = new FreshAccountsCheck(FreshAccountsProperties.defaults());

    @Test
    void freshFarmScoresHigh() {
        List<ActorProfile> actors = new ArrayList<>();
        actors.addAll(actors("farm", 22, daysAgo(5)));
        actors.addAll(actors("old", 3, daysAgo(1000)));

        CheckResult result = check.evaluate(snapshot(actors, DataAvailability.pocDefaults()));

        assertThat(result.checkId()).isEqualTo(CheckIds.FRESH_ACCOUNTS);
        assertThat(result.score()).isGreaterThanOrEqualTo(0.9);
        assertThat(result.confidence()).isEqualTo(0.9);
        assertThat(result.details())
                .containsEntry("mocked", false)
                .containsEntry("star_actors", 25)
                .containsEntry("fresh_actors", 22)
                .containsEntry("new_accounts", 22)
                .containsEntry("empty_accounts", 0)
                .containsEntry("fresh_share", 0.88)
                .containsEntry("coverage", 1.0)
                .doesNotContainKey("reason");
    }

    @Test
    void organicScoresLow() {
        List<ActorProfile> actors = new ArrayList<>();
        actors.addAll(actors("new", 3, daysAgo(10)));
        actors.addAll(actors("dev", 27, daysAgo(700)));

        CheckResult result = check.evaluate(snapshot(actors, DataAvailability.pocDefaults()));

        assertThat(result.score()).isLessThanOrEqualTo(0.1);
        assertThat(result.confidence()).isEqualTo(0.9);
        assertThat(result.details()).containsEntry("fresh_share", 0.1);
    }

    @Test
    void noActorDataGivesLowConfidenceAndZeroScore() {
        CheckResult result = check.evaluate(snapshot(actors("acc", 25, null), DataAvailability.pocDefaults()));

        assertThat(result.confidence()).isLessThan(0.5);
        assertThat(result.score()).isZero();
        assertThat(result.details())
                .containsEntry("reason", "no_actor_data")
                .containsEntry("actors_with_data", 0)
                .containsEntry("actor_history", false);
    }

    @Test
    void snapshotWithoutProfilesIsInactive() {
        RepoSnapshot snapshot = new RepoSnapshot(new RepoRef("demo", "repo"), WINDOW, WINDOW.to(),
                EventCounts.empty(), 0, 0, null, DataAvailability.pocDefaults());

        CheckResult result = check.evaluate(snapshot);

        assertThat(result.score()).isZero();
        assertThat(result.confidence()).isZero();
        assertThat(result.details()).containsEntry("reason", "no_star_actors");
    }

    @Test
    void firstSeenProxyIsUsedOnlyWithActorHistory() {
        List<ActorProfile> actors = actors("acc", 20, null, daysAgo(3), daysAgo(1));

        CheckResult withHistory = check.evaluate(snapshot(actors, WITH_HISTORY));
        CheckResult withoutHistory = check.evaluate(snapshot(actors, DataAvailability.pocDefaults()));

        assertThat(withHistory.score()).isEqualTo(1.0);
        assertThat(withHistory.confidence()).isEqualTo(0.9);
        assertThat(withHistory.details()).containsEntry("new_accounts", 20);
        assertThat(withoutHistory.score()).isZero();
        assertThat(withoutHistory.confidence()).isZero();
    }

    @Test
    void oldAccountsWithoutOtherActivityAreEmpty() {
        List<ActorProfile> actors = new ArrayList<>();
        actors.addAll(actors("idle", 10, daysAgo(900), daysAgo(400), daysAgo(200)));
        actors.addAll(actors("lurker", 8, daysAgo(900), daysAgo(400), null));
        actors.addAll(actors("active", 2, daysAgo(900), daysAgo(400), daysAgo(1)));

        CheckResult result = check.evaluate(snapshot(actors, WITH_HISTORY));

        assertThat(result.score()).isCloseTo(1.0, within(1e-9));
        assertThat(result.details())
                .containsEntry("empty_accounts", 18)
                .containsEntry("new_accounts", 0);
    }

    @Test
    void emptinessIsIgnoredWithoutActorHistory() {
        List<ActorProfile> actors = actors("acc", 20, daysAgo(900), STARRED_AT, null);

        CheckResult result = check.evaluate(snapshot(actors, DataAvailability.pocDefaults()));

        assertThat(result.score()).isZero();
        assertThat(result.details()).containsEntry("empty_accounts", 0);
    }

    @Test
    void accountCreatedAtTakesPrecedenceOverFirstSeen() {
        List<ActorProfile> actors = actors("acc", 20, daysAgo(900), daysAgo(3), daysAgo(1));

        CheckResult result = check.evaluate(snapshot(actors, WITH_HISTORY));

        assertThat(result.score()).isZero();
        assertThat(result.details()).containsEntry("fresh_actors", 0);
    }

    @Test
    void accountExactlyAtNewAccountDaysIsNotFresh() {
        List<ActorProfile> actors = actors("acc", 20, WINDOW.to().minus(Duration.ofDays(30)));

        assertThat(check.evaluate(snapshot(actors, DataAvailability.pocDefaults())).score()).isZero();
    }

    @Test
    void gateScalesSmallSample() {
        CheckResult result = check.evaluate(snapshot(actors("acc", 5, daysAgo(2)), DataAvailability.pocDefaults()));

        assertThat(result.score()).isCloseTo(0.5, within(1e-9));
        assertThat(result.details()).containsEntry("gate", 0.5);
    }

    @Test
    void partialCoverageLowersConfidenceAndCountsUnknownAsNotFresh() {
        List<ActorProfile> actors = new ArrayList<>();
        actors.addAll(actors("known", 10, daysAgo(2)));
        actors.addAll(actors("unknown", 10, null));

        CheckResult result = check.evaluate(snapshot(actors, DataAvailability.pocDefaults()));

        assertThat(result.confidence()).isCloseTo(0.45, within(1e-9));
        assertThat(result.score()).isCloseTo(1.0 / 3, within(1e-9));
        assertThat(result.details())
                .containsEntry("reason", "partial_actor_data")
                .containsEntry("coverage", 0.5);
    }

    @Test
    void detailsContainLimitedSortedSampleOfHashes() {
        List<ActorProfile> actors = actors("acc", 12, daysAgo(2));

        CheckResult result = check.evaluate(snapshot(actors, DataAvailability.pocDefaults()));

        List<String> expected = actors.stream().map(ActorProfile::actorHash).sorted().limit(5).toList();
        assertThat(result.details()).containsEntry("fresh_actor_sample", expected);
    }

    @Test
    void usesThresholdsFromProperties() {
        FreshAccountsCheck custom = new FreshAccountsCheck(
                new FreshAccountsProperties(CheckMode.REAL, 7, 90, 0.2, 0.4, 20, 0.8, 0));
        List<ActorProfile> actors = new ArrayList<>();
        actors.addAll(actors("new", 10, daysAgo(3)));
        actors.addAll(actors("week", 10, daysAgo(10)));

        CheckResult result = custom.evaluate(snapshot(actors, DataAvailability.pocDefaults()));

        assertThat(result.score()).isCloseTo(0.75, within(1e-9));
        assertThat(result.confidence()).isEqualTo(0.8);
        assertThat(result.details()).containsEntry("fresh_actor_sample", List.of());
    }

    @Test
    void rejectsInvalidProperties() {
        assertThatThrownBy(() -> new FreshAccountsProperties(null, 0, null, null, null, null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new FreshAccountsProperties(null, null, 0, null, null, null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new FreshAccountsProperties(null, null, null, 1.0, null, null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new FreshAccountsProperties(null, null, null, null, 0.0, null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new FreshAccountsProperties(null, null, null, null, null, 0, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new FreshAccountsProperties(null, null, null, null, null, null, 1.5, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new FreshAccountsProperties(null, null, null, null, null, null, null, -1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void typoInModeFailsStartupInsteadOfDroppingCheck() {
        new ApplicationContextRunner()
                .withUserConfiguration(PropertiesConfig.class)
                .withPropertyValues(FreshAccountsProperties.MODE_PROPERTY + "=reall")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void modeBindsFromProperty() {
        new ApplicationContextRunner()
                .withUserConfiguration(PropertiesConfig.class)
                .withPropertyValues(FreshAccountsProperties.MODE_PROPERTY + "=real")
                .run(context -> assertThat(context.getBean(FreshAccountsProperties.class).mode())
                        .isEqualTo(CheckMode.REAL));
    }

    private static Instant daysAgo(int days) {
        return WINDOW.to().minus(Duration.ofDays(days));
    }

    private static List<ActorProfile> actors(String prefix, int count, Instant accountCreatedAt) {
        return actors(prefix, count, accountCreatedAt, STARRED_AT, null);
    }

    private static List<ActorProfile> actors(
            String prefix, int count, Instant accountCreatedAt, Instant firstSeen, Instant lastActivityAt) {
        List<ActorProfile> actors = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            actors.add(new ActorProfile(prefix + "-" + i, firstSeen, accountCreatedAt, lastActivityAt));
        }
        return actors;
    }

    private static RepoSnapshot snapshot(List<ActorProfile> actors, DataAvailability data) {
        EventCounts counts = new EventCounts(actors.size(), actors.size(), 0, 0, 0, 0, 0);
        return new RepoSnapshot(new RepoRef("demo", "repo"), WINDOW, WINDOW.to(),
                counts, actors.size(), actors.size(), null, data, actors);
    }

    @EnableConfigurationProperties(FreshAccountsProperties.class)
    static class PropertiesConfig {
    }
}
