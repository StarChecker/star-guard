package ru.itmo.ipkn.starguard.core.github;

import feign.FeignException;
import feign.Request;
import feign.Response;
import org.junit.jupiter.api.Test;
import ru.itmo.ipkn.starguard.core.config.GithubProperties;
import ru.itmo.ipkn.starguard.core.domain.ActorProfile;
import ru.itmo.ipkn.starguard.core.domain.EventType;
import ru.itmo.ipkn.starguard.core.domain.GhEvent;
import ru.itmo.ipkn.starguard.core.domain.RepoRef;
import ru.itmo.ipkn.starguard.core.domain.TimeWindow;
import ru.itmo.ipkn.starguard.core.stats.RepoAggregate;
import ru.itmo.ipkn.starguard.core.stats.StatsAggregator;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AccountAgeEnricherTest {

    private static final RepoRef REPO = new RepoRef("demo", "repo");
    private static final TimeWindow WINDOW = new TimeWindow(
            Instant.parse("2026-11-10T00:00:00Z"), Instant.parse("2026-11-10T03:00:00Z"), 3);
    private static final Instant CREATED = Instant.parse("2026-11-05T00:00:00Z");

    private final MutableClock clock = new MutableClock(Instant.parse("2026-11-10T04:00:00Z"));
    private final StatsAggregator aggregator = new StatsAggregator(clock);

    @Test
    void disabledEnricherDoesNotCallGithub() {
        FakeClient client = new FakeClient(login -> new GithubUser(login, CREATED));
        AccountAgeEnricher enricher = enricher(client, false, 100, 50);
        RepoAggregate aggregate = aggregate(stars(3));

        assertThat(enricher.enrich(aggregate, 1)).isSameAs(aggregate);
        assertThat(client.calls).isEmpty();
    }

    @Test
    void fillsAccountCreatedAtAndCachesLookups() {
        FakeClient client = new FakeClient(login -> new GithubUser(login, CREATED));
        AccountAgeEnricher enricher = enricher(client, true, 100, 50);

        RepoAggregate enriched = enricher.enrich(aggregate(stars(3)), 1);
        enricher.enrich(aggregate(stars(3)), 1);

        assertThat(client.calls).containsExactly("user1", "user2", "user3");
        assertThat(enriched.snapshot().starActorProfiles())
                .extracting(ActorProfile::accountCreatedAt)
                .containsOnly(CREATED);
        assertThat(enriched.starActors()).allMatch(actor -> CREATED.equals(actor.accountCreatedAt()));
    }

    @Test
    void notFoundIsCachedAsUnknown() {
        FakeClient client = new FakeClient(login -> {
            throw error(404, Map.of());
        });
        AccountAgeEnricher enricher = enricher(client, true, 100, 50);

        RepoAggregate enriched = enricher.enrich(aggregate(stars(2)), 1);
        enricher.enrich(aggregate(stars(2)), 1);

        assertThat(client.calls).hasSize(2);
        assertThat(enriched.snapshot().starActorProfiles())
                .extracting(ActorProfile::accountCreatedAt)
                .containsOnlyNulls();
    }

    @Test
    void transientErrorIsNotCached() {
        List<Boolean> failOnce = new ArrayList<>(List.of(true));
        FakeClient client = new FakeClient(login -> {
            if (!failOnce.isEmpty()) {
                failOnce.clear();
                throw new IllegalStateException("timeout");
            }
            return new GithubUser(login, CREATED);
        });
        AccountAgeEnricher enricher = enricher(client, true, 100, 50);

        RepoAggregate first = enricher.enrich(aggregate(stars(1)), 1);
        RepoAggregate second = enricher.enrich(aggregate(stars(1)), 1);

        assertThat(first.snapshot().starActorProfiles().getFirst().accountCreatedAt()).isNull();
        assertThat(second.snapshot().starActorProfiles().getFirst().accountCreatedAt()).isEqualTo(CREATED);
    }

    @Test
    void rateLimitPausesLookupsUntilReset() {
        Instant reset = clock.instant().plus(Duration.ofMinutes(10));
        List<Boolean> limited = new ArrayList<>(List.of(true));
        FakeClient client = new FakeClient(login -> {
            if (!limited.isEmpty()) {
                limited.clear();
                throw error(403, Map.of("X-RateLimit-Reset", List.of(String.valueOf(reset.getEpochSecond()))));
            }
            return new GithubUser(login, CREATED);
        });
        AccountAgeEnricher enricher = enricher(client, true, 100, 50);

        enricher.enrich(aggregate(stars(3)), 1);
        assertThat(client.calls).hasSize(1);

        clock.set(reset.minusSeconds(1));
        enricher.enrich(aggregate(stars(3)), 1);
        assertThat(client.calls).hasSize(1);

        clock.set(reset);
        RepoAggregate resumed = enricher.enrich(aggregate(stars(3)), 1);
        assertThat(client.calls).hasSize(4);
        assertThat(resumed.snapshot().starActorProfiles())
                .extracting(ActorProfile::accountCreatedAt)
                .containsOnly(CREATED);
    }

    @Test
    void limitsLookupsPerRequestAndFillsGapsOnNextRequests() {
        FakeClient client = new FakeClient(login -> new GithubUser(login, CREATED));
        AccountAgeEnricher enricher = enricher(client, true, 100, 2);

        RepoAggregate first = enricher.enrich(aggregate(stars(5)), 1);
        assertThat(client.calls).containsExactly("user1", "user2");
        assertThat(first.snapshot().starActorProfiles())
                .filteredOn(profile -> profile.accountCreatedAt() != null)
                .hasSize(2);

        enricher.enrich(aggregate(stars(5)), 1);
        assertThat(client.calls).containsExactly("user1", "user2", "user3", "user4");
    }

    @Test
    void enrichesOnlyTopNIndexedRepositories() {
        FakeClient client = new FakeClient(login -> new GithubUser(login, CREATED));
        AccountAgeEnricher enricher = enricher(client, true, 10, 50);

        enricher.enrich(aggregate(stars(1)), 11);
        enricher.enrich(aggregate(stars(1)), -1);
        assertThat(client.calls).isEmpty();

        enricher.enrich(aggregate(stars(1)), 10);
        assertThat(client.calls).containsExactly("user1");
    }

    @Test
    void enrichesDirectArchiveReportsWithoutRank() {
        FakeClient client = new FakeClient(login -> new GithubUser(login, CREATED));
        AccountAgeEnricher enricher = enricher(client, true, 10, 50);

        enricher.enrich(aggregate(stars(1)), null);

        assertThat(client.calls).containsExactly("user1");
    }

    @Test
    void skipsActorsWithKnownDateOrWithoutLogin() {
        FakeClient client = new FakeClient(login -> new GithubUser(login, CREATED));
        AccountAgeEnricher enricher = enricher(client, true, 100, 50);
        List<GhEvent> events = List.of(
                new GhEvent("1", EventType.WATCH, 1, REPO.fullName(), 1, "known",
                        WINDOW.from().plusSeconds(60), CREATED),
                new GhEvent("2", EventType.WATCH, 1, REPO.fullName(), 2, "",
                        WINDOW.from().plusSeconds(120)));

        enricher.enrich(aggregate(events), 1);

        assertThat(client.calls).isEmpty();
    }

    @Test
    void rejectsInvalidProperties() {
        assertThatThrownBy(() -> new GithubProperties(true, null, null, 0, null, null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new GithubProperties(true, null, null, null, -1, null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new GithubProperties(true, null, null, null, null, Duration.ZERO, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new GithubProperties(true, null, null, null, null, null, 0L, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(GithubProperties.defaults().enabled()).isFalse();
    }

    private AccountAgeEnricher enricher(FakeClient client, boolean enabled, int topN, int maxLookups) {
        GithubProperties properties = new GithubProperties(
                enabled, null, null, topN, maxLookups, Duration.ofHours(24), 1000L, Duration.ofMinutes(1));
        return new AccountAgeEnricher(client, properties, clock);
    }

    private RepoAggregate aggregate(List<GhEvent> events) {
        return aggregator.aggregate(REPO, WINDOW, events);
    }

    private static List<GhEvent> stars(int count) {
        List<GhEvent> events = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            events.add(new GhEvent(String.valueOf(i), EventType.WATCH, 1, REPO.fullName(), i, "user" + i,
                    WINDOW.from().plusSeconds(60L * i)));
        }
        return events;
    }

    private static FeignException error(int status, Map<String, Collection<String>> headers) {
        Request request = Request.create(Request.HttpMethod.GET, "https://api.github.com/users/x",
                Map.of(), null, StandardCharsets.UTF_8, null);
        Response response = Response.builder()
                .status(status)
                .reason("error")
                .request(request)
                .headers(new HashMap<>(headers))
                .build();
        return FeignException.errorStatus("GithubUsersFeignClient#user", response);
    }

    private static final class FakeClient implements GithubUsersFeignClient {

        private final Function<String, GithubUser> handler;
        private final List<String> calls = new ArrayList<>();

        private FakeClient(Function<String, GithubUser> handler) {
            this.handler = handler;
        }

        @Override
        public GithubUser user(String login) {
            calls.add(login);
            return handler.apply(login);
        }
    }

    private static final class MutableClock extends Clock {

        private Instant now;

        private MutableClock(Instant now) {
            this.now = now;
        }

        private void set(Instant instant) {
            now = instant;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
