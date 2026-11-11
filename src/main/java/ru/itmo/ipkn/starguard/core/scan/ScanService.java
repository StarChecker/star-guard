package ru.itmo.ipkn.starguard.core.scan;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import ru.itmo.ipkn.starguard.core.archive.ArchiveProgress;
import ru.itmo.ipkn.starguard.core.archive.ArchiveUnavailableException;
import ru.itmo.ipkn.starguard.core.archive.TimeWindowCalculator;
import ru.itmo.ipkn.starguard.core.domain.RepoRef;
import ru.itmo.ipkn.starguard.core.domain.TimeWindow;
import ru.itmo.ipkn.starguard.core.github.GithubUnavailableException;
import ru.itmo.ipkn.starguard.core.github.RepoGoneException;
import ru.itmo.ipkn.starguard.core.service.RepoNotFoundException;
import ru.itmo.ipkn.starguard.core.service.RepoReportService;

import java.time.Clock;
import java.time.Duration;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Сканы по запросу для репозиториев вне индекса: кнопка «проверить» в расширении.
 * Скан идёт в фоне, клиент опрашивает статус. Одинаковые запросы (репозиторий + окно)
 * склеиваются в один скан, готовые результаты живут {@link #TTL}.
 */
@Service
public class ScanService {

    static final Duration TTL = Duration.ofMinutes(30);
    private static final int MAX_JOBS = 500;
    private static final int WORKERS = 2;
    private static final Logger log = LoggerFactory.getLogger(ScanService.class);

    private final RepoReportService reportService;
    private final TimeWindowCalculator windowCalculator;
    private final Clock clock;
    private final Cache<String, ScanJob> jobs = Caffeine.newBuilder()
            .expireAfterWrite(TTL)
            .maximumSize(MAX_JOBS)
            .build();
    private final ConcurrentHashMap<String, String> jobIdByKey = new ConcurrentHashMap<>();
    private final ExecutorService executor;

    public ScanService(RepoReportService reportService, TimeWindowCalculator windowCalculator, Clock clock) {
        this.reportService = reportService;
        this.windowCalculator = windowCalculator;
        this.clock = clock;
        AtomicInteger threads = new AtomicInteger();
        this.executor = Executors.newFixedThreadPool(WORKERS, runnable -> {
            Thread thread = new Thread(runnable, "repo-scan-" + threads.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        });
    }

    /** Запускает скан по умолчанию: история GitHub, если есть токен, иначе GH Archive. */
    public ScanJob start(RepoRef repo, Integer requestedHours) {
        return start(repo, requestedHours, null);
    }

    /**
     * Запускает скан или возвращает уже идущий/готовый для того же репозитория, окна и источника.
     *
     * @param source null — выбрать автоматически
     */
    public ScanJob start(RepoRef repo, Integer requestedHours, ScanJob.Source source) {
        TimeWindow window = windowCalculator.window(requestedHours);
        ScanJob.Source chosen = source != null
                ? source
                : reportService.historyAvailable() ? ScanJob.Source.GITHUB : ScanJob.Source.ARCHIVE;
        String key = repo.fullName().toLowerCase(Locale.ROOT) + "|" + chosen + "|"
                + (chosen == ScanJob.Source.GITHUB ? "history" : window.from() + "|" + window.hours());
        synchronized (jobIdByKey) {
            String existingId = jobIdByKey.get(key);
            ScanJob existing = existingId == null ? null : jobs.getIfPresent(existingId);
            if (existing != null && existing.status() != ScanJob.Status.FAILED) {
                return existing;
            }
            ScanJob job = new ScanJob(UUID.randomUUID().toString(), repo, window.hours(), chosen, clock.instant());
            jobs.put(job.id(), job);
            jobIdByKey.put(key, job.id());
            executor.submit(() -> run(job, requestedHours));
            return job;
        }
    }

    public Optional<ScanJob> find(String id) {
        return Optional.ofNullable(jobs.getIfPresent(id));
    }

    private void run(ScanJob job, Integer requestedHours) {
        job.running();
        try {
            ArchiveProgress progress = job::progress;
            job.done(job.source() == ScanJob.Source.GITHUB
                    ? reportService.historyReport(job.repo(), null, progress)
                    : reportService.scanArchive(job.repo(), requestedHours, null, progress));
        } catch (RepoGoneException e) {
            job.failed("REPO_NOT_FOUND_ON_GITHUB", e.getMessage());
        } catch (GithubUnavailableException e) {
            job.failed(e.code(), e.getMessage());
        } catch (RepoNotFoundException e) {
            job.failed("REPO_NOT_FOUND_IN_WINDOW", e.getMessage());
        } catch (ArchiveUnavailableException e) {
            job.failed("ARCHIVE_UNAVAILABLE", e.getMessage());
        } catch (RuntimeException e) {
            log.error("Scan {} for {} failed", job.id(), job.repo().fullName(), e);
            job.failed("INTERNAL_ERROR", "Unexpected error during scan");
        }
    }

    @PreDestroy
    void shutdown() {
        executor.shutdownNow();
    }
}
