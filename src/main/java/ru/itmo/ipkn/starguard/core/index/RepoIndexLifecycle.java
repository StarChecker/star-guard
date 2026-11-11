package ru.itmo.ipkn.starguard.core.index;

import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import ru.itmo.ipkn.starguard.core.config.ArchiveProperties;
import ru.itmo.ipkn.starguard.core.config.RepoIndexProperties;
import ru.itmo.ipkn.starguard.core.metrics.IndexMemoryMonitor;
import ru.itmo.ipkn.starguard.core.metrics.StarGuardMetrics;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Фоновый прогрев и почасовое обновление индекса.
 * Сборка идёт в одном потоке; подмена snapshot на {@link RepoIndex} атомарная.
 */
public class RepoIndexLifecycle {

    private static final Logger log = LoggerFactory.getLogger(RepoIndexLifecycle.class);

    private final RepoIndexBuilder builder;
    private final RepoIndex repoIndex;
    private final RepoIndexProperties properties;
    private final boolean fixedWindow;
    private final IndexMemoryMonitor memoryMonitor;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "repo-index-builder");
        thread.setDaemon(true);
        return thread;
    });
    private final AtomicBoolean building = new AtomicBoolean();

    public RepoIndexLifecycle(
            RepoIndexBuilder builder,
            RepoIndex repoIndex,
            RepoIndexProperties properties) {
        this(builder, repoIndex, properties, false);
    }

    public RepoIndexLifecycle(
            RepoIndexBuilder builder,
            RepoIndex repoIndex,
            RepoIndexProperties properties,
            ArchiveProperties archiveProperties) {
        this(builder, repoIndex, properties, archiveProperties.windowEnd() != null, null);
    }

    /** С контролем бюджета памяти (F3-6). */
    public RepoIndexLifecycle(
            RepoIndexBuilder builder,
            RepoIndex repoIndex,
            RepoIndexProperties properties,
            ArchiveProperties archiveProperties,
            IndexMemoryMonitor memoryMonitor) {
        this(builder, repoIndex, properties, archiveProperties.windowEnd() != null, memoryMonitor);
    }

    private RepoIndexLifecycle(
            RepoIndexBuilder builder,
            RepoIndex repoIndex,
            RepoIndexProperties properties,
            boolean fixedWindow) {
        this(builder, repoIndex, properties, fixedWindow, null);
    }

    private RepoIndexLifecycle(
            RepoIndexBuilder builder,
            RepoIndex repoIndex,
            RepoIndexProperties properties,
            boolean fixedWindow,
            IndexMemoryMonitor memoryMonitor) {
        this.builder = builder;
        this.repoIndex = repoIndex;
        this.properties = properties;
        this.fixedWindow = fixedWindow;
        this.memoryMonitor = memoryMonitor;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        if (Boolean.TRUE.equals(properties.buildOnStartup())) {
            refreshAsync();
        }
    }

    @Scheduled(cron = "${app.index.refresh-cron:0 10 * * * *}", zone = "UTC")
    public void refreshAsync() {
        if (fixedWindow && repoIndex.snapshot().isPresent()) {
            return;
        }
        if (!building.compareAndSet(false, true)) {
            log.info("Repository index build is already in progress; skipping trigger");
            return;
        }
        try {
            executor.submit(() -> {
                Timer.Sample sample = Timer.start(Metrics.globalRegistry);
                String outcome = "failure";
                try {
                    RepoIndexSnapshot snapshot = builder.build();
                    repoIndex.swap(snapshot);
                    outcome = "success";
                    log.info("Repository index swapped: builtAt={}, window={}h, repos={}",
                            snapshot.builtAt(), snapshot.window().hours(), snapshot.repos().size());
                    if (memoryMonitor != null) {
                        memoryMonitor.afterSwap(snapshot.repos().size());
                    }
                } catch (RuntimeException e) {
                    log.error("Repository index build failed; keeping previous snapshot", e);
                } finally {
                    sample.stop(Timer.builder(StarGuardMetrics.INDEX_BUILD_DURATION)
                            .description("Сборка индекса топ-N")
                            .tag("outcome", outcome)
                            .register(Metrics.globalRegistry));
                    building.set(false);
                }
            });
        } catch (RejectedExecutionException e) {
            building.set(false);
            log.warn("Repository index executor is shut down; refresh skipped");
        }
    }

    @PreDestroy
    void shutdown() {
        executor.shutdownNow();
    }
}
