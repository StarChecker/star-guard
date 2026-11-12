package ru.itmo.ipkn.starguard.core.tracker;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public class TrackerStore {

    private static final RowMapper<TrackedRepo> TRACKED = (rs, row) -> new TrackedRepo(
            rs.getLong("id"),
            rs.getString("node_id"),
            rs.getString("full_name"),
            instant(rs.getTimestamp("repo_created_at")),
            rs.getInt("stars"),
            rs.getInt("forks"),
            (Integer) rs.getObject("popular_rank"),
            instant(rs.getTimestamp("watched_until")),
            instant(rs.getTimestamp("tracked_since")),
            instant(rs.getTimestamp("last_polled_at")));

    private static final RowMapper<Point> POINT = (rs, row) -> new Point(
            rs.getTimestamp(1).toInstant(), rs.getInt(2), rs.getInt(3));

    private static final String TRACKED_COLUMNS = """
            id, node_id, full_name, repo_created_at, stars, forks, popular_rank,
            watched_until, tracked_since, last_polled_at
            """;

    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;

    public TrackerStore(JdbcTemplate jdbc, TransactionTemplate transactions) {
        this.jdbc = jdbc;
        this.transactions = transactions;
    }

    public void replacePopular(List<NewRepo> ranked, Instant now) {
        transactions.executeWithoutResult(status -> {
            jdbc.update("update tracked_repo set popular_rank = null where popular_rank is not null");
            List<Object[]> rows = new ArrayList<>(ranked.size());
            for (int i = 0; i < ranked.size(); i++) {
                NewRepo repo = ranked.get(i);
                rows.add(new Object[]{repo.nodeId(), repo.fullName(), lower(repo.fullName()),
                        timestamp(repo.createdAt()), repo.stars(), repo.forks(), i + 1, timestamp(now)});
            }
            jdbc.batchUpdate("""
                    insert into tracked_repo (node_id, full_name, full_name_lc, repo_created_at, stars, forks,
                                              popular_rank, tracked_since)
                    values (?, ?, ?, ?, ?, ?, ?, ?)
                    on conflict (node_id) do update set
                        full_name = excluded.full_name,
                        full_name_lc = excluded.full_name_lc,
                        repo_created_at = coalesce(excluded.repo_created_at, tracked_repo.repo_created_at),
                        popular_rank = excluded.popular_rank
                    """, rows);
        });
    }

    public boolean watch(NewRepo repo, Instant until, int maxWatched, Instant now) {
        Integer existing = jdbc.query("select 1 from tracked_repo where node_id = ?",
                (rs, row) -> 1, repo.nodeId()).stream().findFirst().orElse(null);
        if (existing == null) {
            Integer watched = jdbc.queryForObject("""
                    select count(*) from tracked_repo where popular_rank is null and watched_until > ?
                    """, Integer.class, timestamp(now));
            if (watched != null && watched >= maxWatched) {
                return false;
            }
        }
        jdbc.update("""
                insert into tracked_repo (node_id, full_name, full_name_lc, repo_created_at, stars, forks,
                                          watched_until, tracked_since)
                values (?, ?, ?, ?, ?, ?, ?, ?)
                on conflict (node_id) do update set
                    full_name = excluded.full_name,
                    full_name_lc = excluded.full_name_lc,
                    repo_created_at = coalesce(excluded.repo_created_at, tracked_repo.repo_created_at),
                    watched_until = greatest(coalesce(tracked_repo.watched_until, excluded.watched_until),
                                             excluded.watched_until)
                """,
                repo.nodeId(), repo.fullName(), lower(repo.fullName()), timestamp(repo.createdAt()),
                repo.stars(), repo.forks(), timestamp(until), timestamp(now));
        return true;
    }

    public List<TrackedRepo> active(Instant now) {
        return jdbc.query("select " + TRACKED_COLUMNS + """
                from tracked_repo
                where popular_rank is not null or watched_until > ? or rising
                order by id
                """, TRACKED, timestamp(now));
    }

    public Optional<TrackedRepo> findByNodeId(String nodeId) {
        return jdbc.query("select " + TRACKED_COLUMNS + " from tracked_repo where node_id = ?", TRACKED, nodeId)
                .stream().findFirst();
    }

    public Optional<TrackedRepo> findByName(String fullName) {
        return jdbc.query("select " + TRACKED_COLUMNS + " from tracked_repo where full_name_lc = ? order by id",
                TRACKED, lower(fullName)).stream().findFirst();
    }

    public void saveSamples(Instant at, List<Sample> samples) {
        if (samples.isEmpty()) {
            return;
        }
        transactions.executeWithoutResult(status -> {
            jdbc.batchUpdate("""
                    insert into star_sample (repo_id, at, stars, forks) values (?, ?, ?, ?)
                    on conflict (repo_id, at) do nothing
                    """, samples.stream()
                    .map(sample -> new Object[]{sample.repoId(), timestamp(at), sample.stars(), sample.forks()})
                    .toList());
            jdbc.batchUpdate("update tracked_repo set stars = ?, forks = ?, last_polled_at = ? where id = ?",
                    samples.stream()
                            .map(sample -> new Object[]{sample.stars(), sample.forks(), timestamp(at), sample.repoId()})
                            .toList());
        });
    }

    public void remove(Collection<Long> repoIds) {
        if (repoIds.isEmpty()) {
            return;
        }
        jdbc.batchUpdate("delete from tracked_repo where id = ?", repoIds.stream().map(id -> new Object[]{id}).toList());
    }

    public void recordRun(PollRun run) {
        jdbc.update("""
                insert into poll_run (at, repos, failed, duration_ms, rate_remaining) values (?, ?, ?, ?, ?)
                on conflict (at) do nothing
                """, timestamp(run.at()), run.repos(), run.failed(), run.durationMs(), run.rateRemaining());
    }

    public Optional<PollRun> lastRun() {
        return jdbc.query("""
                select at, repos, failed, duration_ms, rate_remaining from poll_run order by at desc limit 1
                """, (rs, row) -> new PollRun(rs.getTimestamp(1).toInstant(), rs.getInt(2), rs.getInt(3),
                rs.getInt(4), (Integer) rs.getObject(5))).stream().findFirst();
    }

    public int rollupHourly(Instant from, Instant to) {
        return jdbc.update("""
                insert into star_hourly (repo_id, hour_at, stars, forks)
                select distinct on (repo_id, date_trunc('hour', at, 'UTC'))
                       repo_id, date_trunc('hour', at, 'UTC'), stars, forks
                from star_sample
                where at >= ? and at < ?
                order by repo_id, date_trunc('hour', at, 'UTC'), at desc
                on conflict (repo_id, hour_at) do update set stars = excluded.stars, forks = excluded.forks
                """, timestamp(from), timestamp(to));
    }

    public int purge(Instant rawBefore, Instant hourlyBefore, Instant runsBefore) {
        int removed = jdbc.update("delete from star_sample where at < ?", timestamp(rawBefore));
        removed += jdbc.update("delete from star_hourly where hour_at < ?", timestamp(hourlyBefore));
        jdbc.update("delete from poll_run where at < ?", timestamp(runsBefore));
        return removed;
    }

    public int expireWatched(Instant now) {
        return jdbc.update("""
                delete from tracked_repo
                where popular_rank is null and not rising and (watched_until is null or watched_until < ?)
                """, timestamp(now));
    }

    public List<Point> raw(long repoId, Instant from) {
        return jdbc.query("select at, stars, forks from star_sample where repo_id = ? and at >= ? order by at",
                POINT, repoId, timestamp(from));
    }

    public List<Point> hourly(long repoId, Instant from) {
        return jdbc.query("select hour_at, stars, forks from star_hourly where repo_id = ? and hour_at >= ? order by hour_at",
                POINT, repoId, timestamp(from));
    }

    public long[] starsByHourOfDay(Instant from) {
        long[] bins = new long[24];
        jdbc.query("""
                select extract(hour from hour_at at time zone 'UTC')::int as h, sum(greatest(stars - prev, 0)) as stars
                from (
                    select hour_at, stars,
                           lag(stars) over w as prev,
                           lag(hour_at) over w as prev_hour
                    from star_hourly
                    where hour_at >= ?
                    window w as (partition by repo_id order by hour_at)
                ) t
                where prev is not null and hour_at - prev_hour = interval '1 hour'
                group by h
                """, rs -> {
            bins[rs.getInt(1)] = rs.getLong(2);
        }, timestamp(from));
        return bins;
    }

    public Counts counts(Instant now) {
        return jdbc.queryForObject("""
                select count(*) filter (where popular_rank is not null),
                       count(*) filter (where popular_rank is null and watched_until > ?),
                       count(*) filter (where rising)
                from tracked_repo
                """, (rs, row) -> new Counts(rs.getInt(1), rs.getInt(2), rs.getInt(3)), timestamp(now));
    }

    public int markRising(List<NewRepo> repos, int max, Instant now) {
        Integer current = jdbc.queryForObject("select count(*) from tracked_repo where rising", Integer.class);
        int free = Math.max(0, max - (current == null ? 0 : current));
        int added = 0;
        for (NewRepo repo : repos) {
            if (added >= free) {
                break;
            }
            added += jdbc.update("""
                    insert into tracked_repo (node_id, full_name, full_name_lc, repo_created_at, stars, forks,
                                              tracked_since, rising)
                    values (?, ?, ?, ?, ?, ?, ?, true)
                    on conflict (node_id) do update set rising = true, slow_since = null
                    where not tracked_repo.rising
                    """, repo.nodeId(), repo.fullName(), lower(repo.fullName()), timestamp(repo.createdAt()),
                    repo.stars(), repo.forks(), timestamp(now));
        }
        return added;
    }

    public List<RisingGrowth> risingGrowth(Instant dayAgo) {
        return jdbc.query("""
                select t.id, t.stars, t.slow_since,
                       (select h.stars from star_hourly h
                        where h.repo_id = t.id and h.hour_at <= ? order by h.hour_at desc limit 1) as day_ago
                from tracked_repo t
                where t.rising
                """, (rs, row) -> new RisingGrowth(rs.getLong(1), rs.getInt(2), instant(rs.getTimestamp(3)),
                (Integer) rs.getObject(4)), timestamp(dayAgo));
    }

    public void updateRising(long repoId, boolean rising, Instant slowSince) {
        jdbc.update("update tracked_repo set rising = ?, slow_since = ? where id = ?",
                rising, timestamp(slowSince), repoId);
    }

    private static String lower(String value) {
        return value.toLowerCase(Locale.ROOT);
    }

    private static Timestamp timestamp(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }

    private static Instant instant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }

    public record NewRepo(String nodeId, String fullName, Instant createdAt, int stars, int forks) {
    }

    public record TrackedRepo(
            long id,
            String nodeId,
            String fullName,
            Instant createdAt,
            int stars,
            int forks,
            Integer popularRank,
            Instant watchedUntil,
            Instant trackedSince,
            Instant lastPolledAt
    ) {
    }

    public record Sample(long repoId, int stars, int forks) {
    }

    public record Point(Instant at, int stars, int forks) {
    }

    public record PollRun(Instant at, int repos, int failed, int durationMs, Integer rateRemaining) {
    }

    public record RisingGrowth(long repoId, int stars, Instant slowSince, Integer starsDayAgo) {
    }

    public record Counts(int popular, int watched, int rising) {
    }
}
