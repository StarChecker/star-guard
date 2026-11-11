package ru.itmo.ipkn.starguard.core.index;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import ru.itmo.ipkn.starguard.core.config.ArchiveProperties;
import ru.itmo.ipkn.starguard.core.config.RepoIndexProperties;

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
        this(builder, repoIndex, properties, archiveProperties.windowEnd() != null);
    }

    private RepoIndexLifecycle(
            RepoIndexBuilder builder,
            RepoIndex repoIndex,
            RepoIndexProperties properties,
            boolean fixedWindow) {
        this.builder = builder;
        this.repoIndex = repoIndex;
        this.properties = properties;
        this.fixedWindow = fixedWindow;
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
                try {
                    RepoIndexSnapshot snapshot = builder.build();
                    repoIndex.swap(snapshot);
                    log.info("Repository index swapped: builtAt={}, window={}h, repos={}",
                            snapshot.builtAt(), snapshot.window().hours(), snapshot.repos().size());
                } catch (RuntimeException e) {
                    log.error("Repository index build failed; keeping previous snapshot", e);
                } finally {
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
