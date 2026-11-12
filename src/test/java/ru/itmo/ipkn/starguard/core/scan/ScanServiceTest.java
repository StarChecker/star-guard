package ru.itmo.ipkn.starguard.core.scan;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import ru.itmo.ipkn.starguard.core.archive.ArchiveProgress;
import ru.itmo.ipkn.starguard.core.archive.TimeWindowCalculator;
import ru.itmo.ipkn.starguard.core.config.ArchiveProperties;
import ru.itmo.ipkn.starguard.core.domain.RepoRef;
import ru.itmo.ipkn.starguard.core.github.StarsHiddenException;
import ru.itmo.ipkn.starguard.core.service.RepoReport;
import ru.itmo.ipkn.starguard.core.service.RepoReportService;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ScanServiceTest {

    private static final RepoRef REPO = new RepoRef("acme", "tool");
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-12T10:00:00Z"), ZoneOffset.UTC);
    private static final ArchiveProperties ARCHIVE = new ArchiveProperties(
            "https://example.test", ArchiveProperties.Mode.HTTP,
            6, 24, 2, 4, 0, false, Duration.ofHours(1), null, null);

    private final RepoReportService reports = mock(RepoReportService.class);
    private final ScanService scans = new ScanService(reports, new TimeWindowCalculator(CLOCK, ARCHIVE), CLOCK);

    @AfterEach
    void stop() {
        scans.shutdown();
    }

    @Test
    void hiddenStargazersFallBackToArchiveScan() throws InterruptedException {
        RepoReport report = mock(RepoReport.class);
        when(reports.historyAvailable()).thenReturn(true);
        when(reports.historyReport(eq(REPO), isNull(), any(ArchiveProgress.class)))
                .thenThrow(new StarsHiddenException(REPO, 8400));
        when(reports.scanArchive(eq(REPO), isNull(), isNull(), any(ArchiveProgress.class))).thenReturn(report);

        ScanJob job = scans.start(REPO, null);
        awaitFinished(job);

        assertThat(job.status()).isEqualTo(ScanJob.Status.DONE);
        assertThat(job.source()).isEqualTo(ScanJob.Source.ARCHIVE);
        assertThat(job.report()).isSameAs(report);
    }

    private static void awaitFinished(ScanJob job) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 2000;
        while ((job.status() == ScanJob.Status.QUEUED || job.status() == ScanJob.Status.RUNNING)
                && System.currentTimeMillis() < deadline) {
            Thread.sleep(10);
        }
    }
}
