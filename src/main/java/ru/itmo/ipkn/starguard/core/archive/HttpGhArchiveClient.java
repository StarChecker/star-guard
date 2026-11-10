package ru.itmo.ipkn.starguard.core.archive;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import feign.Response;
import feign.RetryableException;
import jakarta.annotation.PreDestroy;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import ru.itmo.ipkn.starguard.core.config.ArchiveProperties;
import ru.itmo.ipkn.starguard.core.config.StarIndexProperties;
import ru.itmo.ipkn.starguard.core.domain.EventType;
import ru.itmo.ipkn.starguard.core.domain.GhEvent;
import ru.itmo.ipkn.starguard.core.domain.RepoRef;
import ru.itmo.ipkn.starguard.core.domain.StarIndex;
import ru.itmo.ipkn.starguard.core.domain.TimeWindow;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.zip.GZIPInputStream;

/**
 * Скачивает часовые дампы GH Archive через Feign, потоково распаковывает gzip
 * и оставляет события запрошенного репозитория.
 * <p>
 * В том же проходе по дампу собирается {@link StarIndex} — звёзды всех репозиториев часа.
 * Он кэшируется по часу отдельно от событий репозитория и нужен проверке C4.
 */
@Component
@ConditionalOnProperty(name = "app.archive.mode", havingValue = "http", matchIfMissing = true)
public class HttpGhArchiveClient implements GhArchiveClient {

    private static final Logger log = LoggerFactory.getLogger(HttpGhArchiveClient.class);
    private static final DateTimeFormatter HOUR_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd-H", Locale.ROOT);
    private static final long BACKOFF_MILLIS = 200L;
    private static final long MAX_CACHE_ENTRIES = 256L;

    private final GhArchiveFeignClient feignClient;
    private final GhArchiveParser parser;
    private final ArchiveProperties properties;
    private final ExecutorService executor;
    private final Cache<@NonNull String, CachedHour> cache;
    private final StarIndexProperties starIndexProperties;
    private final Cache<@NonNull Instant, StarIndex> starIndexCache;

    public HttpGhArchiveClient(
            GhArchiveFeignClient feignClient,
            GhArchiveParser parser,
            ArchiveProperties properties,
            StarIndexProperties starIndexProperties) {
        this.feignClient = feignClient;
        this.parser = parser;
        this.properties = properties;
        this.starIndexProperties = starIndexProperties;

        AtomicInteger threadCounter = new AtomicInteger();
        this.executor = Executors.newFixedThreadPool(properties.parallelism(), runnable -> {
            Thread thread = new Thread(runnable, "gharchive-fetch-" + threadCounter.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        });
        this.cache = Caffeine.newBuilder()
                .expireAfterWrite(properties.cacheTtl())
                .maximumSize(MAX_CACHE_ENTRIES)
                .build();
        this.starIndexCache = Caffeine.newBuilder()
                .expireAfterWrite(properties.cacheTtl())
                .maximumSize(starIndexProperties.cacheHours())
                .build();
    }

    @PreDestroy
    void shutdown() {
        executor.shutdownNow();
    }

    @Override
    public ArchiveFetchResult fetchEvents(RepoRef repo, TimeWindow window) {
        return fetchEvents(repo, window, ArchiveProgress.NONE);
    }

    @Override
    public ArchiveFetchResult fetchEvents(RepoRef repo, TimeWindow window, ArchiveProgress progress) {
        List<Instant> hours = window.hourStarts();
        List<PendingHour> pending = new ArrayList<>(hours.size());
        AtomicInteger done = new AtomicInteger();
        for (Instant hour : hours) {
            pending.add(new PendingHour(hour, executor.submit(() -> {
                try {
                    return loadHour(repo, hour);
                } finally {
                    progress.hourDone(done.incrementAndGet(), hours.size());
                }
            })));
        }

        List<HourResult> results = new ArrayList<>(hours.size());
        for (PendingHour item : pending) {
            try {
                results.add(item.future().get());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new ArchiveUnavailableException("Interrupted while fetching GH Archive", e);
            } catch (ExecutionException e) {
                log.warn("Unexpected GH Archive fetch failure for hour {}", item.hour(), e.getCause());
                results.add(HourResult.failed(item.hour()));
            }
        }

        List<HourResult> failed = results.stream().filter(HourResult::failed).toList();
        if (properties.failFast() && !failed.isEmpty()) {
            throw new ArchiveUnavailableException("GH Archive hours are unavailable: "
                    + failed.stream().map(HourResult::hour).toList());
        }

        List<GhEvent> events = results.stream()
                .flatMap(result -> result.events().stream())
                .filter(event -> !event.createdAt().isBefore(window.from()))
                .filter(event -> event.createdAt().isBefore(window.to()))
                .sorted(Comparator.comparing(GhEvent::createdAt).thenComparing(GhEvent::eventId))
                .toList();

        int hoursLoaded = (int) results.stream()
                .filter(result -> !result.missing() && !result.failed())
                .count();
        List<Instant> missingHours = results.stream()
                .filter(result -> result.missing() || result.failed())
                .map(HourResult::hour)
                .sorted()
                .toList();
        int parseErrors = results.stream().mapToInt(HourResult::parseErrors).sum();
        boolean cached = !results.isEmpty() && results.stream().allMatch(HourResult::cached);
        StarIndex starIndex = StarIndex.merge(results.stream()
                .map(HourResult::starIndex)
                .filter(StarIndex::available)
                .toList());

        return new ArchiveFetchResult(
                events, hours.size(), hoursLoaded, missingHours, parseErrors, cached, starIndex);
    }

    private HourResult loadHour(RepoRef repo, Instant hour) {
        String cacheKey = GhArchiveParser.normalizeFullName(repo.fullName()) + "|" + hour;
        CachedHour cached = cache.getIfPresent(cacheKey);
        if (cached != null) {
            StarIndex hourIndex = starIndexProperties.enabled()
                    ? starIndexCache.getIfPresent(hour)
                    : null;
            return new HourResult(hour, cached.events(), cached.parseErrors(), false, false, true,
                    hourIndex == null ? StarIndex.empty() : hourIndex);
        }

        RuntimeException lastError = null;
        for (int attempt = 0; attempt <= properties.retries(); attempt++) {
            try {
                HourResult result = fetchHour(repo, hour);
                if (!result.missing() && !result.failed()) {
                    cache.put(cacheKey, new CachedHour(result.events(), result.parseErrors()));
                    if (result.starIndex().available()) {
                        starIndexCache.put(hour, result.starIndex());
                    }
                }
                return result;
            } catch (TransientArchiveException | RetryableException e) {
                lastError = e;
                if (attempt < properties.retries()) {
                    sleepBackoff(attempt);
                }
            }
        }

        String repoFullName = repo.fullName();
        log.warn("GH Archive hour {} for {} failed after retries: {}",
                hour, repoFullName, lastError == null ? "unknown" : lastError.getMessage());
        return HourResult.failed(hour);
    }

    private HourResult fetchHour(RepoRef repo, Instant hour) {
        String file = HOUR_FORMAT.format(hour.atZone(ZoneOffset.UTC)) + ".json.gz";
        try (Response response = feignClient.download(file)) {
            if (response.status() == 404) {
                return HourResult.missing(hour);
            }
            if (response.status() < 200 || response.status() >= 300) {
                throw new TransientArchiveException("GH Archive returned status " + response.status());
            }
            if (response.body() == null) {
                throw new TransientArchiveException("GH Archive returned an empty body");
            }

            List<GhEvent> events = new ArrayList<>();
            int parseErrors = 0;
            StarIndex.Builder starIndex = starIndexProperties.enabled()
                    ? new StarIndex.Builder(starIndexProperties.maxPairsPerHour())
                    : null;
            try (InputStream body = response.body().asInputStream();
                 GZIPInputStream gzip = new GZIPInputStream(body);
                 BufferedReader reader = new BufferedReader(
                         new InputStreamReader(gzip, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.isBlank()) {
                        continue;
                    }
                    try {
                        Optional<GhEvent> parsed = parser.parse(line);
                        if (parsed.isEmpty()) {
                            continue;
                        }
                        GhEvent event = parsed.get();
                        if (event.repoFullName().equalsIgnoreCase(repo.fullName())) {
                            events.add(event);
                        }
                        if (starIndex != null && event.type() == EventType.WATCH) {
                            starIndex.add(event.repoFullName(), event.actorId());
                        }
                    } catch (MalformedEventException _) {
                        parseErrors++;
                    }
                }
            }
            StarIndex hourIndex = starIndex == null ? StarIndex.empty() : starIndex.build(1);
            if (hourIndex.available()) {
                log.debug("GH Archive hour {}: star index {} repos, {} pairs{}", hour,
                        hourIndex.repoCount(), hourIndex.pairCount(),
                        hourIndex.truncated() ? " (truncated)" : "");
            }
            return new HourResult(hour, List.copyOf(events), parseErrors, false, false, false, hourIndex);
        } catch (IOException e) {
            throw new TransientArchiveException("I/O error while fetching " + file, e);
        }
    }

    private static void sleepBackoff(int attempt) {
        try {
            Thread.sleep(BACKOFF_MILLIS * (attempt + 1));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ArchiveUnavailableException("Interrupted during GH Archive retry backoff", e);
        }
    }

    private record PendingHour(Instant hour, Future<HourResult> future) {
    }

    private record CachedHour(List<GhEvent> events, int parseErrors) {
    }

    private record HourResult(
            Instant hour,
            List<GhEvent> events,
            int parseErrors,
            boolean missing,
            boolean failed,
            boolean cached,
            StarIndex starIndex
    ) {

        static HourResult missing(Instant hour) {
            return new HourResult(hour, List.of(), 0, true, false, false, StarIndex.empty());
        }

        static HourResult failed(Instant hour) {
            return new HourResult(hour, List.of(), 0, false, true, false, StarIndex.empty());
        }
    }

    private static final class TransientArchiveException extends RuntimeException {

        private TransientArchiveException(String message) {
            super(message);
        }

        private TransientArchiveException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
