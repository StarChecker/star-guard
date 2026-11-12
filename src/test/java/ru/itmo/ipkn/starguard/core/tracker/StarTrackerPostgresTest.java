package ru.itmo.ipkn.starguard.core.tracker;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import ru.itmo.ipkn.starguard.core.config.GithubProperties;
import ru.itmo.ipkn.starguard.core.config.TrackerProperties;
import ru.itmo.ipkn.starguard.core.domain.GithubSignals;
import ru.itmo.ipkn.starguard.core.domain.RepoRef;
import ru.itmo.ipkn.starguard.core.github.GithubGraphqlFeignClient;
import ru.itmo.ipkn.starguard.core.github.GithubTrackerClient;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
class StarTrackerPostgresTest {

    @Container
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    private static HikariDataSource dataSource;

    private final MutableClock clock = new MutableClock(Instant.parse("2026-11-12T10:00:00Z"));
    private final FakeNodes graphql = new FakeNodes();
    private TrackerStore store;
    private StarTracker tracker;

    @BeforeAll
    static void connect() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(POSTGRES.getJdbcUrl());
        config.setUsername(POSTGRES.getUsername());
        config.setPassword(POSTGRES.getPassword());
        dataSource = new HikariDataSource(config);
        new ResourceDatabasePopulator(new ClassPathResource("tracker/schema.sql")).execute(dataSource);
    }

    @AfterAll
    static void close() {
        dataSource.close();
    }

    @BeforeEach
    void setUp() {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        jdbc.update("truncate tracked_repo, star_sample, star_hourly, poll_run");
        store = new TrackerStore(jdbc, new TransactionTemplate(new DataSourceTransactionManager(dataSource)));
        TrackerProperties properties = new TrackerProperties(true, null, null, null, null, null, 2, null,
                Duration.ofDays(30), 2, null, null, 100, Duration.ZERO, 100, 7, null);
        tracker = new StarTracker(store, new GithubTrackerClient(graphql), properties,
                new GithubProperties(true, null, "token", null, null, null, null, null), clock);
    }

    @Test
    void pollsPopularAndWatchedRepositoriesAndBuildsIntraday() {
        store.replacePopular(List.of(
                new TrackerStore.NewRepo("R_top", "big/top", Instant.parse("2015-01-01T00:00:00Z"), 90_000, 9_000)),
                clock.instant());
        tracker.watch("R_new", new RepoRef("new", "repo"), Instant.parse("2026-11-03T00:00:00Z"), 100, 10);

        for (int i = 0; i < 12; i++) {
            graphql.counts.put("R_top", new int[]{90_000 + i * 5, 9_000});
            graphql.counts.put("R_new", new int[]{100 + (i == 11 ? 400 : i), 10});
            TrackerStore.PollRun run = tracker.pollOnce();
            assertThat(run.repos()).isEqualTo(2);
            clock.advance(Duration.ofMinutes(5));
        }

        GithubSignals.Intraday intraday = tracker.intraday("R_new").orElseThrow();
        int[] stars = intraday.stars();
        assertThat(stars[stars.length - 2]).isEqualTo(500);
        assertThat(stars[stars.length - 3]).isEqualTo(110);
        assertThat(tracker.status().popular()).isEqualTo(1);
        assertThat(tracker.status().watched()).isEqualTo(1);
        assertThat(tracker.status().lastRun().repos()).isEqualTo(2);
        assertThat(tracker.find(new RepoRef("NEW", "Repo"))).isPresent();
    }

    @Test
    void rollsUpHoursAndComputesGlobalHourProfile() {
        tracker.watch("R_a", new RepoRef("a", "a"), null, 0, 0);
        int stars = 0;
        for (int i = 0; i < 6; i++) {
            stars += clock.instant().atZone(ZoneOffset.UTC).getHour() == 12 ? 50 : 5;
            graphql.counts.put("R_a", new int[]{stars, 0});
            tracker.pollOnce();
            clock.advance(Duration.ofHours(1));
        }
        tracker.maintain();

        double[] share = tracker.globalHourShare();
        assertThat(share).hasSize(24);
        assertThat(share[12]).isGreaterThan(0.6);
        assertThat(tracker.intraday("R_a").orElseThrow().hourlyStars()).contains(stars);
    }

    @Test
    void respectsWatchCapacityAndExpiresWatchedRepositories() {
        tracker.watch("R_1", new RepoRef("o", "one"), null, 1, 0);
        tracker.watch("R_2", new RepoRef("o", "two"), null, 1, 0);
        tracker.watch("R_3", new RepoRef("o", "three"), null, 1, 0);

        assertThat(tracker.status().watched()).isEqualTo(2);
        assertThat(store.findByNodeId("R_3")).isEmpty();

        clock.advance(Duration.ofDays(31));
        tracker.maintain();
        assertThat(store.findByNodeId("R_1")).isEmpty();
    }

    @Test
    void removesRepositoriesThatDisappearedFromGithub() {
        tracker.watch("R_gone", new RepoRef("o", "gone"), null, 1, 0);

        tracker.pollOnce();

        assertThat(store.findByNodeId("R_gone")).isEmpty();
    }

    static final class FakeNodes implements GithubGraphqlFeignClient {

        final Map<String, int[]> counts = new HashMap<>();

        @Override
        @SuppressWarnings("unchecked")
        public Map<String, Object> query(Map<String, Object> body) {
            List<String> ids = (List<String>) ((Map<String, Object>) body.get("variables")).get("ids");
            List<Object> nodes = new ArrayList<>();
            for (String id : ids) {
                int[] value = counts.get(id);
                nodes.add(value == null ? null
                        : Map.of("id", id, "stargazerCount", value[0], "forkCount", value[1]));
            }
            Map<String, Object> data = new HashMap<>();
            data.put("nodes", nodes);
            data.put("rateLimit", Map.of("remaining", 4000, "resetAt", "2026-11-12T12:00:00Z"));
            return Map.of("data", data);
        }
    }

    static final class MutableClock extends Clock {

        private Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
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
