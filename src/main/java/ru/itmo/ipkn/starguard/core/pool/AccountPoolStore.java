package ru.itmo.ipkn.starguard.core.pool;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import ru.itmo.ipkn.starguard.core.github.GithubAccountPoolClient.Star;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;

public class AccountPoolStore {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    public AccountPoolStore(JdbcTemplate jdbc, TransactionTemplate tx) { this.jdbc = jdbc; this.tx = tx; }

    public List<String> due(Instant before, Instant now, int limit) {
        return jdbc.query("select node_id from suspect_account where risk > 0 and checked_at < ? and next_attempt_at <= ? order by checked_at, node_id limit ?",
                (rs, n) -> rs.getString(1), Timestamp.from(before), Timestamp.from(now), limit);
    }
    public void defer(String id, Instant retryAt) {
        jdbc.update("update suspect_account set next_attempt_at = ? where node_id = ?", Timestamp.from(retryAt), id);
    }
    public boolean recentlyChecked(String id, Instant since) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "select exists(select 1 from suspect_account where node_id = ? and checked_at >= ?)",
                Boolean.class, id, Timestamp.from(since)));
    }
    public void save(String id, double risk, List<Star> stars, Instant now, int maxAccounts) {
        tx.executeWithoutResult(status -> {
            // Serialize top-N replacement across collectors sharing the database.
            jdbc.execute("lock table suspect_account in exclusive mode");
            jdbc.update("""
                insert into suspect_account(node_id, risk, checked_at, next_attempt_at) values (?, ?, ?, ?)
                on conflict(node_id) do update set risk = excluded.risk, checked_at = excluded.checked_at, next_attempt_at = excluded.checked_at
                """, id, risk, Timestamp.from(now), Timestamp.from(now));
            jdbc.update("delete from suspect_star where account_id = ?", id);
            if (risk > 0) {
                jdbc.batchUpdate("""
                    insert into suspect_star(account_id, repo_node_id, full_name_lc, starred_at) values (?, ?, ?, ?)
                    on conflict(account_id, repo_node_id) do nothing
                    """, stars.stream().map(s -> new Object[]{id, s.repoId(), s.fullName().toLowerCase(Locale.ROOT),
                        Timestamp.from(s.at())}).toList());
            }
            // Keep rejected candidates for cooldown, but remove all their star edges.
            jdbc.update("""
                update suspect_account set risk = 0 where node_id in
                (select node_id from suspect_account where risk > 0 order by risk desc, checked_at desc, node_id offset ?)
                """, maxAccounts);
            jdbc.update("delete from suspect_star where account_id in (select node_id from suspect_account where risk = 0)");
        });
    }
    public void purge(Instant stale, int maxAccounts) {
        jdbc.update("delete from suspect_account where checked_at < ?", Timestamp.from(stale));
        jdbc.update("""
            delete from suspect_account where node_id in
            (select node_id from suspect_account where risk = 0 order by checked_at desc, node_id offset ?)
            """, maxAccounts);
        jdbc.update("delete from suspect_seed where repo_id not in (select node_id from tracked_repo)");
    }
    public Optional<Seed> nextSeed(Instant now) {
        return jdbc.query("""
            select t.node_id, s.cursor from tracked_repo t left join suspect_seed s on s.repo_id = t.node_id
            where t.rising or t.popular_rank is not null or t.watched_until > ?
            order by s.checked_at asc nulls first, t.rising desc, t.id limit 1
            """, (rs, n) -> new Seed(rs.getString(1), rs.getString(2)), Timestamp.from(now)).stream().findFirst();
    }
    public void advance(Seed seed, String cursor, Instant now) {
        jdbc.update("""
            insert into suspect_seed(repo_id, cursor, checked_at) values (?, ?, ?)
            on conflict(repo_id) do update set cursor = excluded.cursor, checked_at = excluded.checked_at
            """, seed.id(), cursor, Timestamp.from(now));
    }
    public Evidence evidence(String fullName, String repoNodeId, Instant from, Instant to, Instant freshSince) {
        // Same query/snapshot for pool size and matches, excluding stale accounts even if maintenance failed.
        return jdbc.query("""
            select (select count(*) from suspect_account where risk > 0 and checked_at >= ?) as pool_size,
                   s.starred_at
            from (select 1) anchor left join (
                select starred_at from suspect_star s join suspect_account a on a.node_id = s.account_id
                where a.risk > 0 and a.checked_at >= ? and ((cast(? as text) is not null and s.repo_node_id = ?) or (cast(? as text) is null and s.full_name_lc = ?))
                  and s.starred_at >= ? and s.starred_at < ?
            ) s on true order by s.starred_at
            """, rs -> {
                int size = 0;
                var times = new ArrayList<Instant>();
                while (rs.next()) {
                    size = rs.getInt(1);
                    if (rs.getTimestamp(2) != null) times.add(rs.getTimestamp(2).toInstant());
                }
                return new Evidence(size, List.copyOf(times));
            }, Timestamp.from(freshSince), Timestamp.from(freshSince), repoNodeId, repoNodeId, repoNodeId, fullName.toLowerCase(Locale.ROOT),
                Timestamp.from(from), Timestamp.from(to));
    }
    public record Seed(String id, String cursor) {}
    public record Evidence(int poolSize, List<Instant> times) {}
}
