package ru.itmo.ipkn.starguard.core.index;

import feign.RetryableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.itmo.ipkn.starguard.core.archive.EventFilter;
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
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Строит индекс по почасовым дайджестам ({@link HourDigest}).
 * <p>
 * Каждый час окна скачивается и разбирается <b>один раз</b>: за проход собираются
 * агрегаты всех репозиториев часа и {@link StarIndex} для C4. Топ-N выбирается по сумме
 * звёзд дайджестов, агрегаты топа берутся из них же — второго прохода по дампу нет.
 * <p>
 * Дайджесты загруженных часов живут до следующей сборки: при сдвиге окна разбираются
 * только новые часы и часы, которые в прошлый раз не загрузились (404 или ошибка после
 * ретраев); часы, ушедшие из окна, забываются. Репозиторий, впервые попавший в топ,
 * получает полные данные за прошлые часы из готовых дайджестов, поэтому результат
 * совпадает с полной пересборкой окна.
 * <p>
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
    private final PinnedRepos pins;
    private final Clock clock;

    /** Дайджесты загруженных часов последнего окна; меняется только внутри {@link #build()}. */
    private volatile Map<Instant, HourDigest> retained = Map.of();

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
        this(bulkReader, windowCalculator, archiveProperties, indexProperties,
                starIndexProperties, PinnedRepos.none(), clock);
    }

    /**
     * @param pins закреплённые репозитории (F4-2): попадают в топ, пока у них есть события в окне
     */
    public RepoIndexBuilder(
            GhArchiveBulkReader bulkReader,
            TimeWindowCalculator windowCalculator,
            ArchiveProperties archiveProperties,
            RepoIndexProperties indexProperties,
            StarIndexProperties starIndexProperties,
            PinnedRepos pins,
            Clock clock) {
        this.bulkReader = bulkReader;
        this.windowCalculator = windowCalculator;
        this.archiveProperties = archiveProperties;
        this.indexProperties = indexProperties;
        this.starIndexProperties = starIndexProperties;
        this.pins = pins == null ? PinnedRepos.none() : pins;
        this.clock = clock;
    }

    public synchronized RepoIndexSnapshot build() {
        int requestedHours = indexProperties.windowHours();
        if (requestedHours > archiveProperties.maxWindowHours()) {
            throw new IllegalStateException(
                    "app.index.window-hours must not exceed app.archive.max-window-hours");
        }

        TimeWindow window = windowCalculator.window(requestedHours);
        List<Instant> hours = window.hourStarts();
        long startedAt = System.nanoTime();

        Map<Instant, HourDigest> previous = retained;
        Map<Instant, HourDigest> digests = new HashMap<>();
        List<Instant> toParse = new ArrayList<>();
        for (Instant hour : hours) {
            HourDigest digest = previous.get(hour);
            if (digest != null) {
                digests.put(hour, digest);
            } else {
                toParse.add(hour);
            }
        }
        int reusedHours = digests.size();
        for (HourDigest digest : scanHours(toParse, this::digestHour)) {
            digests.put(digest.hour(), digest);
        }
        retained = digests.values().stream()
                .filter(HourDigest::loaded)
                .collect(Collectors.toUnmodifiableMap(HourDigest::hour, digest -> digest));

        Map<String, Integer> starsByRepo = new HashMap<>();
        Map<String, String> displayNames = new HashMap<>();
        Map<Instant, StarIndex> starIndexByHour = new HashMap<>();
        List<Instant> missingHours = new ArrayList<>();
        int hoursLoaded = 0;
        int parseErrors = 0;
        int digestRepos = 0;
        int digestActors = 0;
        for (Instant hour : hours) {
            HourDigest digest = digests.get(hour);
            digest.repos().forEach((key, aggregate) -> {
                int stars = aggregate.counts().stars();
                if (stars > 0) {
                    starsByRepo.merge(key, stars, Integer::sum);
                }
            });
            // Часы обходятся по порядку: имя репозитория берётся из самого раннего часа окна.
            digest.displayNames().forEach(displayNames::putIfAbsent);
            if (digest.starIndex().available()) {
                starIndexByHour.put(hour, digest.starIndex());
            }
            if (digest.loaded()) {
                hoursLoaded++;
            } else {
                missingHours.add(hour);
            }
            parseErrors += digest.readResult().parseErrors();
            digestRepos += digest.repos().size();
            digestActors += digest.actorRecords();
        }

        Set<String> candidates = TopRepoSelector.select(starsByRepo, indexProperties.topN());
        if (candidates.isEmpty()) {
            throw new IndexBuildException("No WatchEvent found in the index window");
        }
        if (hoursLoaded == 0) {
            throw new IndexBuildException("No GH Archive hours loaded for the index window");
        }
        // Закреплённые (F4-2) остаются в топе, пока у них есть события в окне.
        Set<String> selected = new LinkedHashSet<>(candidates);
        for (String key : pins.keys()) {
            if (!selected.contains(key) && digests.values().stream().anyMatch(d -> d.repos().containsKey(key))) {
                selected.add(key);
            }
        }

        Map<String, IndexedRepo> repos = new LinkedHashMap<>();
        for (String key : selected) {
            List<RepoHourAggregate> perHour = new ArrayList<>(hours.size());
            for (Instant hour : hours) {
                perHour.add(digests.get(hour).aggregateOrEmpty(key));
            }
            repos.put(key, new IndexedRepo(toRepoRef(displayNames.getOrDefault(key, key)), perHour));
        }

        // Дайджесты отдаются в снимок целиком: из них отвечает long-tail (F4-1).
        RepoIndexSnapshot snapshot = new RepoIndexSnapshot(
                window, clock.instant(), hoursLoaded, missingHours, parseErrors, repos, starIndexByHour, digests);
        long millis = Duration.ofNanos(System.nanoTime() - startedAt).toMillis();
        log.info("Built repo index: {} repos, window={}, hoursLoaded={}, missing={}, parseErrors={}, "
                        + "starIndexHours={}, parsedHours={}, reusedHours={}, digestRepos={}, "
                        + "digestActors={}, took={} ms",
                repos.size(), window.hours(), hoursLoaded, missingHours.size(), parseErrors,
                starIndexByHour.size(), toParse.size(), reusedHours, digestRepos, digestActors, millis);
        return snapshot;
    }

    /** Часы, чьи дайджесты сохранены для следующей сборки (для тестов и диагностики). */
    Set<Instant> retainedHours() {
        return retained.keySet();
    }

    /** Один полный проход по часу: агрегаты всех репозиториев и звёзды для C4. */
    private HourDigest digestHour(Instant hour) {
        RuntimeException lastError = null;
        for (int attempt = 0; attempt <= archiveProperties.retries(); attempt++) {
            Map<String, MutableHourAggregate> local = new HashMap<>();
            StarIndex.Builder starIndex = starIndexProperties.enabled()
                    ? new StarIndex.Builder(starIndexProperties.maxPairsPerHour())
                    : null;
            try {
                HourReadResult result = bulkReader.readHour(hour, EventFilter.ALL, event -> {
                    String key = GhArchiveParser.normalizeFullName(event.repoFullName());
                    local.computeIfAbsent(key, ignored -> new MutableHourAggregate(event.repoFullName()))
                            .add(event, hour);
                    if (starIndex != null && event.type() == EventType.WATCH) {
                        starIndex.add(event.repoFullName(), event.actorId());
                    }
                });
                if (result.missing() || result.failed()) {
                    return HourDigest.unavailable(result);
                }
                Map<String, RepoHourAggregate> repos = new HashMap<>(local.size() * 2);
                Map<String, String> names = new HashMap<>(local.size() * 2);
                local.forEach((key, aggregate) -> {
                    repos.put(key, aggregate.toAggregate(hour));
                    names.put(key, aggregate.fullName());
                });
                return new HourDigest(hour, result, repos, names,
                        starIndex == null ? StarIndex.empty() : starIndex.build(1));
            } catch (TransientArchiveException | RetryableException e) {
                lastError = e;
                if (attempt < archiveProperties.retries()) {
                    sleepBackoff(attempt);
                }
            }
        }
        log.warn("Index hour {} failed after retries: {}", hour,
                lastError == null ? "unknown" : lastError.getMessage());
        return HourDigest.unavailable(new HourReadResult(hour, 0, false, true));
    }

    private <T> List<T> scanHours(List<Instant> hours, Function<Instant, T> task) {
        if (hours.isEmpty()) {
            return List.of();
        }
        int threads = Math.max(1, Math.min(archiveProperties.parallelism(), hours.size()));
        ExecutorService executor = Executors.newFixedThreadPool(threads, runnable -> {
            Thread thread = new Thread(runnable, "gharchive-index");
            thread.setDaemon(true);
            return thread;
        });
        try {
            List<Future<T>> futures = new ArrayList<>(hours.size());
            for (Instant hour : hours) {
                futures.add(executor.submit(() -> task.apply(hour)));
            }
            List<T> results = new ArrayList<>(hours.size());
            for (int i = 0; i < futures.size(); i++) {
                try {
                    results.add(futures.get(i).get());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IndexBuildException("Index build interrupted", e);
                } catch (ExecutionException e) {
                    throw new IndexBuildException(
                            "Index build failed for hour " + hours.get(i), e.getCause());
                }
            }
            return results;
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
