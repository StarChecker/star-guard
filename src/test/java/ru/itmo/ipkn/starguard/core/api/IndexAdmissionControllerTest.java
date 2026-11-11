package ru.itmo.ipkn.starguard.core.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import ru.itmo.ipkn.starguard.core.archive.ArchiveFetchResult;
import ru.itmo.ipkn.starguard.core.config.ArchiveProperties;
import ru.itmo.ipkn.starguard.core.domain.EventCounts;
import ru.itmo.ipkn.starguard.core.domain.HourBucket;
import ru.itmo.ipkn.starguard.core.domain.RepoRef;
import ru.itmo.ipkn.starguard.core.domain.RepoStats;
import ru.itmo.ipkn.starguard.core.domain.TimeWindow;
import ru.itmo.ipkn.starguard.core.index.IndexAdmission;
import ru.itmo.ipkn.starguard.core.index.IndexNotReadyException;
import ru.itmo.ipkn.starguard.core.index.PinLimitExceededException;
import ru.itmo.ipkn.starguard.core.scan.ScanJob;
import ru.itmo.ipkn.starguard.core.scan.ScanService;
import ru.itmo.ipkn.starguard.core.scoring.RepoScore;
import ru.itmo.ipkn.starguard.core.scoring.ScoreLevel;
import ru.itmo.ipkn.starguard.core.service.RepoReport;
import ru.itmo.ipkn.starguard.core.service.RepoReportService;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(IndexAdmissionController.class)
@Import({RepoReportMapper.class, ApiExceptionHandler.class})
class IndexAdmissionControllerTest {

    private static final RepoRef REPO = new RepoRef("demo", "star-guard");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private IndexAdmission admission;

    @MockitoBean
    private RepoReportService reportService;

    @MockitoBean
    private ScanService scanService;

    @MockitoBean
    private ArchiveProperties archiveProperties;

    @Test
    void repositoryWithEventsInWindowIsReturnedRightAway() throws Exception {
        when(admission.admit(REPO)).thenReturn(IndexAdmission.Outcome.INDEXED);
        when(reportService.getReport(eq(REPO), eq(3), isNull())).thenReturn(report());

        mockMvc.perform(put("/api/v1/index/repos/demo/star-guard").param("hours", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.repo.full_name").value("demo/star-guard"))
                .andExpect(jsonPath("$.score.level").value("low"));
    }

    @Test
    void repositoryWithoutEventsStartsScanAndReturns202() throws Exception {
        when(admission.admit(REPO)).thenReturn(IndexAdmission.Outcome.NOT_IN_WINDOW);
        when(archiveProperties.maxWindowHours()).thenReturn(24);
        ScanJob job = job("scan-1");
        when(scanService.start(REPO, 24)).thenReturn(job);

        mockMvc.perform(put("/api/v1/index/repos/demo/star-guard"))
                .andExpect(status().isAccepted())
                .andExpect(header().string("Location", "/api/v1/scans/scan-1"))
                .andExpect(jsonPath("$.id").value("scan-1"))
                .andExpect(jsonPath("$.status").value("queued"))
                .andExpect(jsonPath("$.source").value("archive"));
        verify(scanService).start(REPO, 24);
    }

    @Test
    void postWithBodyBehavesLikePut() throws Exception {
        when(admission.admit(REPO)).thenReturn(IndexAdmission.Outcome.NOT_IN_WINDOW);
        ScanJob job = job("scan-2");
        when(scanService.start(REPO, 6)).thenReturn(job);

        mockMvc.perform(post("/api/v1/index/repos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"owner\":\"demo\",\"name\":\"star-guard\",\"hours\":6}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.id").value("scan-2"));
    }

    @Test
    void mapsLimitNotReadyAndValidationErrors() throws Exception {
        doThrow(new PinLimitExceededException(1)).when(admission).admit(REPO);
        mockMvc.perform(put("/api/v1/index/repos/demo/star-guard"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("PIN_LIMIT_REACHED"));

        doThrow(new IndexNotReadyException("warming up")).when(admission).admit(REPO);
        mockMvc.perform(put("/api/v1/index/repos/demo/star-guard"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("INDEX_NOT_READY"));

        mockMvc.perform(put("/api/v1/index/repos/bad$owner/star-guard"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/v1/index/repos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"owner\":\"demo\"}"))
                .andExpect(status().isBadRequest());
    }

    private static ScanJob job(String id) {
        ScanJob job = mock(ScanJob.class);
        when(job.id()).thenReturn(id);
        when(job.repo()).thenReturn(REPO);
        when(job.status()).thenReturn(ScanJob.Status.QUEUED);
        when(job.source()).thenReturn(ScanJob.Source.ARCHIVE);
        when(job.createdAt()).thenReturn(Instant.parse("2026-11-11T12:00:00Z"));
        return job;
    }

    private static RepoReport report() {
        TimeWindow window = new TimeWindow(
                Instant.parse("2026-11-10T03:00:00Z"), Instant.parse("2026-11-10T06:00:00Z"), 3);
        RepoStats stats = new RepoStats(REPO, window, new EventCounts(3, 2, 0, 0, 1, 0, 0), 3, 2, 1,
                List.of(new HourBucket(window.from(), 2)));
        RepoScore score = new RepoScore(5, ScoreLevel.LOW, 0.9, false, List.of(), false,
                List.of(), window.to().plus(Duration.ofHours(1)));
        return new RepoReport(REPO, window, stats, score,
                new ArchiveFetchResult(List.of(), 3, 3, List.of(), 0, true));
    }
}
