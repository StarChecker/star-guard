package ru.itmo.ipkn.starguard.core.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import ru.itmo.ipkn.starguard.core.archive.ArchiveFetchResult;
import ru.itmo.ipkn.starguard.core.domain.EventCounts;
import ru.itmo.ipkn.starguard.core.domain.HourBucket;
import ru.itmo.ipkn.starguard.core.domain.RepoRef;
import ru.itmo.ipkn.starguard.core.domain.RepoStats;
import ru.itmo.ipkn.starguard.core.domain.TimeWindow;
import ru.itmo.ipkn.starguard.core.scoring.CheckContribution;
import ru.itmo.ipkn.starguard.core.scoring.RepoScore;
import ru.itmo.ipkn.starguard.core.scoring.ScoreLevel;
import ru.itmo.ipkn.starguard.core.service.RepoNotFoundException;
import ru.itmo.ipkn.starguard.core.service.RepoReport;
import ru.itmo.ipkn.starguard.core.service.RepoReportService;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(RepoController.class)
@Import({RepoReportMapper.class, ApiExceptionHandler.class})
class RepoControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RepoReportService reportService;

    @Test
    void returnsSnakeCaseReport() throws Exception {
        when(reportService.getReport(any(), any(), any())).thenReturn(sampleReport());

        mockMvc.perform(get("/api/v1/repos/demo/star-guard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.repo.full_name").value("demo/star-guard"))
                .andExpect(jsonPath("$.window.hours").value(3))
                .andExpect(jsonPath("$.stats.total_events").value(10))
                .andExpect(jsonPath("$.stats.stars").value(5))
                .andExpect(jsonPath("$.stats.stars_by_hour[0].stars").value(2))
                .andExpect(jsonPath("$.score.score").value(10))
                .andExpect(jsonPath("$.score.level").value("low"))
                .andExpect(jsonPath("$.score.mocked").value(true))
                .andExpect(jsonPath("$.score.checks[0].id").value("burst"))
                .andExpect(jsonPath("$.archive.hours_loaded").value(3));
    }

    @Test
    void returns404WhenRepoHasNoEventsInWindow() throws Exception {
        RepoRef repo = new RepoRef("demo", "star-guard");
        when(reportService.getReport(any(), any(), any()))
                .thenThrow(new RepoNotFoundException(repo, window()));

        mockMvc.perform(get("/api/v1/repos/demo/star-guard"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("REPO_NOT_FOUND_IN_WINDOW"));
    }

    @Test
    void returns400ForUnknownMockMode() throws Exception {
        mockMvc.perform(get("/api/v1/repos/demo/star-guard").param("mock_mode", "weird"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void returns400ForOutOfRangeHours() throws Exception {
        mockMvc.perform(get("/api/v1/repos/demo/star-guard").param("hours", "0"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void returns400ForInvalidOwner() throws Exception {
        mockMvc.perform(get("/api/v1/repos/bad$owner/star-guard"))
                .andExpect(status().isBadRequest());
    }

    private static RepoReport sampleReport() {
        TimeWindow window = window();
        RepoRef repo = new RepoRef("demo", "star-guard");
        EventCounts counts = new EventCounts(10, 5, 1, 2, 1, 1, 0);
        RepoStats stats = new RepoStats(
                repo, window, counts, 4, 3, 2,
                List.of(new HourBucket(window.from(), 2), new HourBucket(window.from().plusSeconds(3600), 3)));
        CheckContribution burst = new CheckContribution(
                "burst", 0.1, 0.9, 0.3, 3.0, true, null, Map.of("mocked", true));
        RepoScore score = new RepoScore(
                10, ScoreLevel.LOW, 0.9, false, List.of(burst), true,
                List.of(), Instant.parse("2026-11-10T06:00:00Z"));
        ArchiveFetchResult archive = new ArchiveFetchResult(
                List.of(), 3, 3, List.of(), 0, false);
        return new RepoReport(repo, window, stats, score, archive);
    }

    private static TimeWindow window() {
        return new TimeWindow(
                Instant.parse("2026-11-10T03:00:00Z"),
                Instant.parse("2026-11-10T06:00:00Z"),
                3);
    }
}
