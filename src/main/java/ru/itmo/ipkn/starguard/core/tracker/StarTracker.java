package ru.itmo.ipkn.starguard.core.tracker;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.itmo.ipkn.starguard.core.config.GithubProperties;
import ru.itmo.ipkn.starguard.core.config.TrackerProperties;
import ru.itmo.ipkn.starguard.core.domain.GithubSignals;
import ru.itmo.ipkn.starguard.core.domain.RepoRef;
import ru.itmo.ipkn.starguard.core.github.GithubTrackerClient;
import ru.itmo.ipkn.starguard.core.github.GithubUnavailableException;
import ru.itmo.ipkn.starguard.core.service.IntradaySource;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

public class StarTracker implements IntradaySource {

    private static final Logger log = LoggerFactory.getLogger(StarTracker.class);
    private static final int POLL_THREADS = 2;

    private final TrackerStore store;
    private final GithubTrackerClient client;
    private final TrackerProperties properties;
    private final GithubProperties githubProperties;
    private final Clock clock;
    private final AtomicBoolean polling = new AtomicBoolean();
    private final AtomicBoolean refreshing = new AtomicBoolean();
    private final Cache<String, double[]> globalProfile = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofMinutes(30))
            .maximumSize(1)
            .build();
    private volatile Integer lastRateRemaining;

    public StarTracker(
            TrackerStore store,
            GithubTrackerClient client,
            TrackerProperties properties,
            GithubProperties githubProperties,
            Clock clock) {
        this.store = store;
        this.client = client;
        this.properties = properties;
        this.githubProperties = githubProperties;
        this.clock = clock;
    }

    public boolean active() {
        return !githubProperties.token().isEmpty();
    }

    public TrackerStore.PollRun pollOnce() {
        if (!polling.compareAndSet(false, true)) {
            return null;
        }
        try {
            Instant started = clock.instant();
            Instant at = started.truncatedTo(ChronoUnit.SECONDS);
            List<TrackerStore.TrackedRepo> repos = store.active(started);
            List<List<TrackerStore.TrackedRepo>> batches = new ArrayList<>();
            for (int from = 0; from < repos.size(); from += properties.batchSize()) {
                batches.add(repos.subList(from, Math.min(repos.size(), from + properties.batchSize())));
            }
            List<TrackerStore.Sample> samples = new ArrayList<>(repos.size());
            Set<Long> gone = new HashSet<>();
            int failed = 0;
            Integer remaining = null;
            int next = 0;
            // Two batches at a time: a poll fits the interval and stays under the GitHub secondary (compute time) limit.
            try (ExecutorService executor = Executors.newFixedThreadPool(POLL_THREADS)) {
                while (next < batches.size()) {
                    if (remaining != null && remaining < properties.rateLimitReserve()) {
                        log.warn("Star tracker stopped the poll: GitHub rate limit reserve reached ({} left)", remaining);
                        break;
                    }
                    List<List<TrackerStore.TrackedRepo>> round = batches.subList(next, Math.min(batches.size(), next + POLL_THREADS));
                    next += round.size();
                    List<Future<GithubTrackerClient.PollResult>> futures = round.stream()
                            .map(batch -> executor.submit(() -> client.poll(batch.stream().map(TrackerStore.TrackedRepo::nodeId).toList())))
                            .toList();
                    boolean stop = false;
                    for (int i = 0; i < round.size(); i++) {
                        List<TrackerStore.TrackedRepo> batch = round.get(i);
                        try {
                            GithubTrackerClient.PollResult result = futures.get(i).get();
                            remaining = remaining == null ? result.rateLimit().remaining()
                                    : Math.min(remaining, result.rateLimit().remaining());
                            for (TrackerStore.TrackedRepo repo : batch) {
                                GithubTrackerClient.Counts counts = result.counts().get(repo.nodeId());
                                if (counts != null) {
                                    samples.add(new TrackerStore.Sample(repo.id(), counts.stars(), counts.forks()));
                                } else if (result.missing().contains(repo.nodeId())) {
                                    gone.add(repo.id());
                                }
                            }
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            failed += batch.size();
                            stop = true;
                        } catch (ExecutionException e) {
                            failed += batch.size();
                            if (!(e.getCause() instanceof GithubUnavailableException unavailable)) {
                                throw new IllegalStateException("Star tracker batch failed", e.getCause());
                            }
                            log.warn("Star tracker batch failed: {}", unavailable.getMessage());
                            stop |= "GITHUB_RATE_LIMITED".equals(unavailable.code())
                                    || "GITHUB_TOKEN_REQUIRED".equals(unavailable.code());
                        }
                    }
                    if (stop) {
                        break;
                    }
                }
            }
            for (int i = next; i < batches.size(); i++) {
                failed += batches.get(i).size();
            }
            store.saveSamples(at, samples);
            store.remove(gone);
            lastRateRemaining = remaining;
            TrackerStore.PollRun run = new TrackerStore.PollRun(at, samples.size(), failed,
                    (int) Duration.between(started, clock.instant()).toMillis(), remaining);
            store.recordRun(run);
            if (!gone.isEmpty()) {
                log.info("Star tracker removed {} repositories that disappeared from GitHub", gone.size());
            }
            return run;
        } finally {
            polling.set(false);
        }
    }

    public int refreshPopular() {
        if (properties.popularSize() == 0 || !refreshing.compareAndSet(false, true)) {
            return 0;
        }
        try {
            PopularWalker.Result result = new PopularWalker(client, properties.searchPageDelay(),
                    properties.rateLimitReserve()).walk(properties.popularSize());
            if (result.rateRemaining() != null) {
                lastRateRemaining = result.rateRemaining();
            }
            if (!result.ranked().isEmpty()) {
                store.replacePopular(result.ranked(), clock.instant());
            }
            log.info("Star tracker refreshed popular repositories: {}{}", result.ranked().size(),
                    result.complete() ? "" : " (stopped early by GitHub rate limit)");
            return result.ranked().size();
        } finally {
            refreshing.set(false);
        }
    }

    public int discoverRising() {
        TrackerProperties.Rising rising = properties.rising();
        if (!rising.enabled()) {
            return 0;
        }
        Instant now = clock.instant();
        String created = now.minus(Duration.ofDays(rising.maxAgeDays())).atZone(java.time.ZoneOffset.UTC)
                .toLocalDate().toString();
        PopularWalker.Result result = new PopularWalker(client, properties.searchPageDelay(),
                properties.rateLimitReserve()).walk(rising.max(), "created:>=" + created, rising.minStars());
        if (result.rateRemaining() != null) {
            lastRateRemaining = result.rateRemaining();
        }
        List<TrackerStore.NewRepo> fast = result.ranked().stream()
                .filter(repo -> repo.createdAt() != null)
                .filter(repo -> repo.stars() / (double) Math.max(1, Duration.between(repo.createdAt(), now).toDays())
                        >= rising.dayStars())
                .toList();
        int added = store.markRising(fast, rising.max(), now);
        log.info("Star tracker rising discovery: {} new repositories found, {} fast, {} started tracking",
                result.ranked().size(), fast.size(), added);
        return added;
    }

    public void reviewRising() {
        TrackerProperties.Rising rising = properties.rising();
        Instant now = clock.instant();
        for (TrackerStore.RisingGrowth growth : store.risingGrowth(now.minus(Duration.ofDays(1)))) {
            if (growth.starsDayAgo() == null) {
                continue;
            }
            boolean slow = growth.stars() - growth.starsDayAgo() < rising.exitDayStars();
            if (!slow) {
                if (growth.slowSince() != null) {
                    store.updateRising(growth.repoId(), true, null);
                }
            } else if (growth.slowSince() == null) {
                store.updateRising(growth.repoId(), true, now);
            } else if (!growth.slowSince().plus(Duration.ofDays(rising.exitDays())).isAfter(now)) {
                store.updateRising(growth.repoId(), false, null);
            }
        }
    }

    public void maintain() {
        Instant now = clock.instant();
        Instant hour = now.truncatedTo(ChronoUnit.HOURS);
        Instant rawFrom = now.minus(properties.rawRetention());
        // Resume from the last completed rollup after downtime; retain a two-hour overlap.
        Instant rollupFrom = store.latestHourlyAt()
                .map(last -> last.minus(Duration.ofHours(2)))
                .orElse(rawFrom);
        if (rollupFrom.isBefore(rawFrom)) {
            rollupFrom = rawFrom;
        }
        store.rollupHourly(rollupFrom, hour);
        globalProfile.invalidateAll();
        reviewRising();
        store.purge(now.minus(properties.rawRetention()), now.minus(properties.hourlyRetention()),
                now.minus(properties.hourlyRetention()));
        int expired = store.expireWatched(now);
        if (expired > 0) {
            log.info("Star tracker stopped watching {} repositories", expired);
        }
    }

    @Override
    public void watch(String nodeId, RepoRef repo, Instant createdAt, long stars, long forks) {
        Instant now = clock.instant();
        boolean added = store.watch(new TrackerStore.NewRepo(nodeId, repo.fullName(), createdAt,
                (int) Math.min(Integer.MAX_VALUE, stars), (int) Math.min(Integer.MAX_VALUE, forks)),
                now.plus(properties.watchTtl()), properties.maxWatched(), now);
        if (!added) {
            log.debug("Star tracker watch list is full, {} is not tracked", repo.fullName());
        }
    }

    public Optional<TrackerStore.TrackedRepo> watch(RepoRef repo) {
        Optional<GithubTrackerClient.RepoHit> hit = client.repository(repo);
        hit.ifPresent(found -> watch(found.nodeId(), canonical(found.fullName(), repo), found.createdAt(),
                found.stars(), found.forks()));
        return hit.flatMap(found -> store.findByNodeId(found.nodeId()));
    }

    private static RepoRef canonical(String fullName, RepoRef fallback) {
        int slash = fullName.indexOf('/');
        return slash > 0 && slash < fullName.length() - 1
                ? new RepoRef(fullName.substring(0, slash), fullName.substring(slash + 1))
                : fallback;
    }

    @Override
    public Optional<GithubSignals.Intraday> intraday(String nodeId) {
        return store.findByNodeId(nodeId).flatMap(this::intraday);
    }

    public Optional<TrackerStore.TrackedRepo> find(RepoRef repo) {
        return store.findByName(repo.fullName());
    }

    public Optional<GithubSignals.Intraday> intraday(TrackerStore.TrackedRepo repo) {
        Instant now = clock.instant();
        List<TrackerStore.Point> raw = store.raw(repo.id(), IntradayAssembler.rawFrom(now));
        if (raw.isEmpty()) {
            return Optional.empty();
        }
        List<TrackerStore.Point> hourly = store.hourly(repo.id(),
                IntradayAssembler.hourlyFrom(now, properties.profileDays()));
        return Optional.of(IntradayAssembler.assemble(now, raw, hourly, properties.profileDays(),
                globalHourShare(), repo.trackedSince()));
    }

    public double[] globalHourShare() {
        return globalProfile.get("global", key -> {
            long[] bins = store.starsByHourOfDay(clock.instant().minus(Duration.ofDays(properties.profileDays())));
            long total = 0;
            for (long bin : bins) {
                total += bin;
            }
            if (total == 0) {
                return new double[0];
            }
            double[] share = new double[24];
            for (int hour = 0; hour < 24; hour++) {
                share[hour] = (double) bins[hour] / total;
            }
            return share;
        });
    }

    public Status status() {
        Instant now = clock.instant();
        TrackerStore.Counts counts = store.counts(now);
        return new Status(active(), counts.popular(), counts.watched(), counts.rising(), store.lastRun().orElse(null),
                lastRateRemaining, properties.pollInterval());
    }

    public List<TrackerStore.Point> rawSeries(TrackerStore.TrackedRepo repo, Duration span) {
        return store.raw(repo.id(), clock.instant().minus(span));
    }

    public List<TrackerStore.Point> hourlySeries(TrackerStore.TrackedRepo repo, Duration span) {
        return store.hourly(repo.id(), clock.instant().minus(span));
    }

    public record Status(
            boolean active,
            int popular,
            int watched,
            int rising,
            TrackerStore.PollRun lastRun,
            Integer rateLimitRemaining,
            Duration pollInterval
    ) {
    }
}
