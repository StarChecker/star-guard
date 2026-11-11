package ru.itmo.ipkn.starguard.core.index;

import feign.RetryableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.itmo.ipkn.starguard.core.archive.GhArchiveBulkReader;
import ru.itmo.ipkn.starguard.core.archive.GhArchiveParser;
import ru.itmo.ipkn.starguard.core.archive.HourReadResult;
import ru.itmo.ipkn.starguard.core.archive.TimeWindowCalculator;
import ru.itmo.ipkn.starguard.core.archive.TransientArchiveException;
import ru.itmo.ipkn.starguard.core.config.ArchiveProperties;
import ru.itmo.ipkn.starguard.core.config.RepoIndexProperties;
import ru.itmo.ipkn.starguard.core.config.StarIndexProperties;
import ru.itmo.ipkn.starguard.core.domain.ActorActivity;
import ru.itmo.ipkn.starguard.core.domain.EventCounts;
import ru.itmo.ipkn.starguard.core.domain.EventType;
import ru.itmo.ipkn.starguard.core.domain.GhEvent;
import ru.itmo.ipkn.starguard.core.domain.RepoRef;
import ru.itmo.ipkn.starguard.core.domain.StarIndex;
import ru.itmo.ipkn.starguard.core.domain.TimeWindow;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Function;

/**
 * Строит индекс в два прохода:
 * 1) считает звёзды по репозиториям за окно и выбирает топ-N; в том же проходе
 *    собирает почасовой {@link StarIndex} всех репозиториев для C4;
 * 2) повторно читает часы и агрегирует только выбранные репозитории.
 * Фоновый процесс; запросы пользователей идут через готовый snapshot.
 */
public class RepoIndexBuilder {

    private static final Logger log = LoggerFactory.getLogger(RepoIndexBuilder.class);
    private static final long BACKOFF_MILLIS = 200L;

    private final GhArchiveBulkReader bulkReader;
    private final TimeWindowCalculator windowCalculator;
    private final ArchiveProperties archiveProperties;
    private final RepoIndexProperties indexProperties;
    private final StarIndexProperties starIndexProperties;
    private final Clock clock;

    public RepoIndexBuilder(
            GhArchiveBulkReader bulkReader,
            TimeWindowCalculator windowCalculator,
            ArchiveProperties archiveProperties,
            RepoIndexProperties indexProperties,
            Clock clock) {
        this(bulkReader, windowCalculator, archiveProperties, indexProperties,
                StarIndexProperties.defaults(), clock);
    }

    public RepoIndexBuilder(
            GhArchiveBulkReader bulkReader,
            TimeWindowCalculator windowCalculator,
            ArchiveProperties archiveProperties,
            RepoIndexProperties indexProperties,
            StarIndexProperties starIndexProperties,
            Clock clock) {
        this.bulkReader = bulkReader;
        this.windowCalculator = windowCalculator;
        this.archiveProperties = archiveProperties;
        this.indexProperties = indexProperties;
        this.starIndexProperties = starIndexProperties;
        this.clock = clock;
    }

    public RepoIndexSnapshot build() {
        int requestedHours = indexProperties.windowHours();
        if (requestedHours > archiveProperties.maxWindowHours()) {
            throw new IllegalStateException(
                    "app.index.window-hours must not exceed app.archive.max-window-hours");
        }

        TimeWindow window = windowCalculator.window(requestedHours);
        List<Instant> hours = window.hourStarts();
        long startedAt = System.nanoTime();

        List<HourScan<PhaseOne>> starScans =
                scanHours(hours, this::phaseOneHour);
        Map<String, Integer> starsByRepo = new HashMap<>();
        Map<Instant, StarIndex> starIndexByHour = new HashMap<>();
        for (HourScan<PhaseOne> scan : starScans) {
            scan.data().starsByRepo().forEach((repo, stars) -> starsByRepo.merge(repo, stars, Integer::sum));
            if (scan.data().starIndex().available()) {
                starIndexByHour.put(scan.hour(), scan.data().starIndex());
            }
        }

        Set<String> candidates = TopRepoSelector.select(starsByRepo, indexProperties.topN());
        if (candidates.isEmpty()) {
            throw new IndexBuildException("No WatchEvent found in the index window");
        }

        List<HourScan<Map<String, MutableHourAggregate>>> detailScans =
                scanHours(hours, hour -> phaseTwoHour(hour, candidates));

        Map<Instant, Map<String, MutableHourAggregate>> byHour = new HashMap<>();
        Map<String, String> displayNames = new HashMap<>();
        List<Instant> missingHours = new ArrayList<>();
        int hoursLoaded = 0;
        int parseErrors = 0;
        for (HourScan<Map<String, MutableHourAggregate>> scan : detailScans) {
            byHour.put(scan.hour(), scan.data());
            scan.data().forEach((key, aggregate) ->
                    displayNames.putIfAbsent(key, aggregate.fullName()));
            if (scan.readResult().missing() || scan.readResult().failed()) {
                missingHours.add(scan.hour());
            } else {
                hoursLoaded++;
            }
            parseErrors += scan.readResult().parseErrors();
        }
        if (hoursLoaded == 0) {
            throw new IndexBuildException("No GH Archive hours loaded for the index window");
        }
        missingHours.sort(Comparator.naturalOrder());

        Map<String, List<RepoHourAggregate>> perRepo = new LinkedHashMap<>();
        for (String key : candidates) {
            perRepo.put(key, new ArrayList<>(hours.size()));
        }
        for (Instant hour : hours) {
            Map<String, MutableHourAggregate> hourData = byHour.getOrDefault(hour, Map.of());
            for (String key : candidates) {
                MutableHourAggregate aggregate = hourData.get(key);
                perRepo.get(key).add(aggregate == null
                        ? RepoHourAggregate.empty(hour)
                        : aggregate.toAggregate(hour));
            }
        }

        Map<String, IndexedRepo> repos = new LinkedHashMap<>();
        for (String key : candidates) {
            String fullName = displayNames.getOrDefault(key, key);
            repos.put(key, new IndexedRepo(toRepoRef(fullName), List.copyOf(perRepo.get(key))));
        }

        RepoIndexSnapshot snapshot = new RepoIndexSnapshot(
                window, clock.instant(), hoursLoaded, missingHours, parseErrors, repos, starIndexByHour);
        long millis = Duration.ofNanos(System.nanoTime() - startedAt).toMillis();
        log.info("Built repo index: {} repos, window={}, hoursLoaded={}, missing={}, parseErrors={}, "
                        + "starIndexHours={}, took={} ms",
                repos.size(), window.hours(), hoursLoaded, missingHours.size(), parseErrors,
                starIndexByHour.size(), millis);
        return snapshot;
    }

    private HourScan<PhaseOne> phaseOneHour(Instant hour) {
        RuntimeException lastError = null;
        for (int attempt = 0; attempt <= archiveProperties.retries(); attempt++) {
            Map<String, Integer> local = new HashMap<>();
            StarIndex.Builder starIndex = starIndexProperties.enabled()
                    ? new StarIndex.Builder(starIndexProperties.maxPairsPerHour())
                    : null;
            try {
                HourReadResult result = bulkReader.readHour(hour, event -> {
                    if (event.type() == EventType.WATCH) {
                        local.merge(GhArchiveParser.normalizeFullName(event.repoFullName()),
                                1, Integer::sum);
                        if (starIndex != null) {
                            starIndex.add(event.repoFullName(), event.actorId());
                        }
                    }
                });
                boolean loaded = !result.missing() && !result.failed();
                StarIndex hourIndex = starIndex != null && loaded
                        ? starIndex.build(1)
                        : StarIndex.empty();
                return new HourScan<>(hour, new PhaseOne(local, hourIndex), result);
            } catch (TransientArchiveException | RetryableException e) {
                lastError = e;
                if (attempt < archiveProperties.retries()) {
                    sleepBackoff(attempt);
                }
            }
        }
        log.warn("Index phase 1 failed for hour {}: {}", hour,
                lastError == null ? "unknown" : lastError.getMessage());
        return new HourScan<>(hour, new PhaseOne(Map.of(), StarIndex.empty()),
                new HourReadResult(hour, 0, false, true));
    }

    private HourScan<Map<String, MutableHourAggregate>> phaseTwoHour(
            Instant hour, Set<String> candidates) {
        RuntimeException lastError = null;
        for (int attempt = 0; attempt <= archiveProperties.retries(); attempt++) {
            Map<String, MutableHourAggregate> local = new HashMap<>();
            try {
                HourReadResult result = bulkReader.readHour(hour, event -> {
                    String key = GhArchiveParser.normalizeFullName(event.repoFullName());
                    if (candidates.contains(key)) {
                        local.computeIfAbsent(key,
                                        ignored -> new MutableHourAggregate(event.repoFullName()))
                                .add(event, hour);
                    }
                });
                return new HourScan<>(hour, local, result);
            } catch (TransientArchiveException | RetryableException e) {
                lastError = e;
                if (attempt < archiveProperties.retries()) {
                    sleepBackoff(attempt);
                }
            }
        }
        log.warn("Index phase 2 failed for hour {}: {}", hour,
                lastError == null ? "unknown" : lastError.getMessage());
        return new HourScan<>(hour, Map.of(), new HourReadResult(hour, 0, false, true));
    }

    private <T> List<HourScan<T>> scanHours(
            List<Instant> hours, Function<Instant, HourScan<T>> task) {
        int threads = Math.max(1, Math.min(archiveProperties.parallelism(), hours.size()));
        ExecutorService executor = Executors.newFixedThreadPool(threads, runnable -> {
            Thread thread = new Thread(runnable, "gharchive-index");
            thread.setDaemon(true);
            return thread;
        });
        try {
            List<Future<HourScan<T>>> futures = new ArrayList<>(hours.size());
            for (Instant hour : hours) {
                futures.add(executor.submit(() -> task.apply(hour)));
            }
            List<HourScan<T>> scans = new ArrayList<>(hours.size());
            for (int i = 0; i < futures.size(); i++) {
                try {
                    scans.add(futures.get(i).get());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IndexBuildException("Index build interrupted", e);
                } catch (ExecutionException e) {
                    throw new IndexBuildException(
                            "Index build failed for hour " + hours.get(i), e.getCause());
                }
            }
            return scans;
        } finally {
            executor.shutdownNow();
        }
    }

    private static RepoRef toRepoRef(String fullName) {
        int slash = fullName.indexOf('/');
        if (slash <= 0 || slash == fullName.length() - 1) {
            throw new IndexBuildException("Invalid repository name in GH Archive: " + fullName);
        }
        return new RepoRef(fullName.substring(0, slash), fullName.substring(slash + 1));
    }

    private static void sleepBackoff(int attempt) {
        try {
            Thread.sleep(BACKOFF_MILLIS * (attempt + 1));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IndexBuildException("Interrupted during index retry backoff", e);
        }
    }

    private record HourScan<T>(Instant hour, T data, HourReadResult readResult) {
    }

    private record PhaseOne(Map<String, Integer> starsByRepo, StarIndex starIndex) {
    }

    private static final class MutableHourAggregate {

        private final String fullName;
        private int total;
        private int stars;
        private int forks;
        private int pushes;
        private int issues;
        private int pullRequests;
        private int other;
        private final int[] minutes = new int[60];
        private final Map<Long, ActorActivity> actors = new HashMap<>();
        private final Set<Long> starActors = new HashSet<>();

        private MutableHourAggregate(String fullName) {
            this.fullName = fullName;
        }

        private String fullName() {
            return fullName;
        }

        private void add(GhEvent event, Instant hour) {
            total++;
            actors.merge(event.actorId(), ActorActivity.of(event), ActorActivity::merge);
            switch (event.type()) {
                case WATCH -> {
                    stars++;
                    starActors.add(event.actorId());
                    int minute = (int) Duration.between(hour, event.createdAt()).toMinutes();
                    if (minute >= 0 && minute < minutes.length) {
                        minutes[minute]++;
                    }
                }
                case FORK -> forks++;
                case PUSH -> pushes++;
                case ISSUES -> issues++;
                case PULL_REQUEST -> pullRequests++;
                case OTHER -> other++;
            }
        }

        private RepoHourAggregate toAggregate(Instant hour) {
            return new RepoHourAggregate(
                    hour,
                    new EventCounts(total, stars, forks, pushes, issues, pullRequests, other),
                    minutes,
                    actors.size(),
                    starActors.size(),
                    List.copyOf(actors.values()));
        }
    }
}
