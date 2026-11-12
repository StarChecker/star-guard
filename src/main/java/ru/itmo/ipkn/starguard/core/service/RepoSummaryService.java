package ru.itmo.ipkn.starguard.core.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import ru.itmo.ipkn.starguard.core.archive.ArchiveUnavailableException;
import ru.itmo.ipkn.starguard.core.config.ArchiveProperties;
import ru.itmo.ipkn.starguard.core.domain.RepoRef;
import ru.itmo.ipkn.starguard.core.github.GithubUnavailableException;
import ru.itmo.ipkn.starguard.core.github.RepoGoneException;
import ru.itmo.ipkn.starguard.core.index.IndexNotReadyException;
import ru.itmo.ipkn.starguard.core.index.RepoNotIndexedException;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Дешёвые сводки для списков и бейджа: только индекс (или фикстуры).
 * Прямой скан GH Archive здесь не запускается — он стоит минуты и трафик.
 */
@Service
public class RepoSummaryService {

    private static final Logger log = LoggerFactory.getLogger(RepoSummaryService.class);

    private static final int GITHUB_PARALLELISM = 6;

    private final RepoReportService reportService;
    private final ArchiveProperties archiveProperties;
    private final ExecutorService githubPool = Executors.newFixedThreadPool(GITHUB_PARALLELISM, new ThreadFactory());

    public RepoSummaryService(RepoReportService reportService, ArchiveProperties archiveProperties) {
        this.reportService = reportService;
        this.archiveProperties = archiveProperties;
    }

    public RepoSummary summarize(RepoRef repo, Integer hours) {
        boolean cheap = reportService.githubFirst() || reportService.hasIndex()
                || archiveProperties.mode() == ArchiveProperties.Mode.FIXTURE;
        if (!cheap) {
            return new RepoSummary(repo, RepoSummary.Status.NOT_INDEXED, null);
        }
        try {
            return new RepoSummary(repo, RepoSummary.Status.OK, reportService.getReport(repo, hours, null));
        } catch (RepoNotIndexedException e) {
            return new RepoSummary(repo, RepoSummary.Status.NOT_INDEXED, null);
        } catch (RepoNotFoundException e) {
            return new RepoSummary(repo, RepoSummary.Status.NO_DATA, null);
        } catch (IndexNotReadyException e) {
            return new RepoSummary(repo, RepoSummary.Status.INDEX_NOT_READY, null);
        } catch (RepoGoneException e) {
            return new RepoSummary(repo, RepoSummary.Status.NO_DATA, null);
        } catch (GithubUnavailableException e) {
            log.debug("Summary for {} failed: {}", repo.fullName(), e.getMessage());
            return new RepoSummary(repo, RepoSummary.Status.ERROR, null);
        } catch (ArchiveUnavailableException | IllegalArgumentException e) {
            log.debug("Summary for {} failed: {}", repo.fullName(), e.getMessage());
            return new RepoSummary(repo, RepoSummary.Status.ERROR, null);
        }
    }

    public List<RepoSummary> summarize(List<RepoRef> repos, Integer hours) {
        if (reportService.githubFirst() && repos.size() > 1) {
            List<CompletableFuture<RepoSummary>> futures = repos.stream()
                    .map(repo -> CompletableFuture.supplyAsync(() -> summarize(repo, hours), githubPool))
                    .toList();
            return futures.stream().map(CompletableFuture::join).toList();
        }
        List<RepoSummary> result = new ArrayList<>(repos.size());
        for (RepoRef repo : repos) {
            result.add(summarize(repo, hours));
        }
        return result;
    }

    private static final class ThreadFactory implements java.util.concurrent.ThreadFactory {

        private final AtomicInteger counter = new AtomicInteger();

        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "github-summary-" + counter.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        }
    }
}
