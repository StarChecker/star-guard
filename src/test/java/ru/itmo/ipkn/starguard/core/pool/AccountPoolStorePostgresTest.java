package ru.itmo.ipkn.starguard.core.pool;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import ru.itmo.ipkn.starguard.core.github.GithubAccountPoolClient.Star;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static ru.itmo.ipkn.starguard.core.pool.PoolFixtures.NOW;

@Testcontainers(disabledWithoutDocker = true)
class AccountPoolStorePostgresTest {
    @Container
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");
    private static HikariDataSource dataSource;
    private AccountPoolStore store;

    @BeforeAll
    static void connect() {
        var config = new HikariConfig();
        config.setJdbcUrl(POSTGRES.getJdbcUrl());
        config.setUsername(POSTGRES.getUsername());
        config.setPassword(POSTGRES.getPassword());
        dataSource = new HikariDataSource(config);
        var schema = new ResourceDatabasePopulator(new ClassPathResource("tracker/schema.sql"));
        schema.execute(dataSource);
        schema.execute(dataSource); // Startup schema must be repeatable.
    }

    @AfterAll
    static void close() { dataSource.close(); }

    @BeforeEach
    void setUp() {
        var jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("truncate suspect_account, suspect_star, suspect_seed");
        store = new AccountPoolStore(jdbc, new TransactionTemplate(new DataSourceTransactionManager(dataSource)));
    }

    @Test
    void keepsTopNAndReplacesStarsAtomicallyOnRefresh() {
        var star = new Star("R1", "owner/repo", NOW.minusSeconds(60));
        store.save("U1", 0.3, List.of(star), NOW, 1);
        store.save("U2", 0.9, List.of(star, star), NOW, 1);
        assertThat(evidence("owner/repo", "R1").poolSize()).isEqualTo(1);
        assertThat(evidence("owner/repo", "R1").times()).hasSize(1);
        store.save("U2", 0.9, List.of(), NOW, 1);
        assertThat(evidence("owner/repo", "R1").times()).isEmpty();
        store.save("U2", 0, List.of(star), NOW, 1);
        assertThat(evidence("owner/repo", "R1").poolSize()).isZero();
    }

    @Test
    void usesStableRepositoryIdAndHalfOpenWindowAndExcludesStaleProfiles() {
        store.save("U1", 1, List.of(new Star("R1", "old/name", NOW.minusSeconds(3600))), NOW, 10);
        store.save("U2", 1, List.of(new Star("R1", "old/name", NOW)), NOW, 10);
        store.save("U3", 1, List.of(new Star("R1", "old/name", NOW.minusSeconds(60))),
                NOW.minus(Duration.ofDays(4)), 10);
        assertThat(evidence("new/name", "R1").times()).containsExactly(NOW.minusSeconds(3600));
        assertThat(evidence("old/name", "R2").times()).isEmpty();
        assertThat(evidence("OLD/NAME", null).times()).containsExactly(NOW.minusSeconds(3600));
        assertThat(evidence("old/name", "R1").poolSize()).isEqualTo(2);
    }

    @Test
    void backoffPreservesFreshnessAndPruningCascadesStars() {
        var old = NOW.minus(Duration.ofDays(2));
        store.save("U1", 1, List.of(new Star("R1", "owner/repo", NOW.minusSeconds(60))), old, 10);
        assertThat(store.due(NOW.minus(Duration.ofDays(1)), NOW, 10)).containsExactly("U1");
        store.defer("U1", NOW.plusSeconds(3600));
        assertThat(store.due(NOW.minus(Duration.ofDays(1)), NOW, 10)).isEmpty();
        assertThat(evidence("owner/repo", "R1").times()).hasSize(1);
        store.purge(NOW.minus(Duration.ofDays(1)), 10);
        assertThat(evidence("owner/repo", "R1").poolSize()).isZero();
        assertThat(evidence("owner/repo", "R1").times()).isEmpty();
    }

    private AccountPoolStore.Evidence evidence(String name, String node) {
        return store.evidence(name, node, NOW.minusSeconds(3600), NOW, NOW.minus(Duration.ofDays(3)));
    }
}
