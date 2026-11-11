package ru.itmo.ipkn.starguard.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import ru.itmo.ipkn.starguard.core.domain.RepoRef;
import ru.itmo.ipkn.starguard.core.index.RepoIndex;
import ru.itmo.ipkn.starguard.core.index.RepoIndexBuilder;
import ru.itmo.ipkn.starguard.core.scan.ScanJob;
import ru.itmo.ipkn.starguard.core.scan.ScanService;

import java.time.Instant;

import static org.hamcrest.Matchers.hasItem;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * F4-2 сквозь HTTP на настоящем индексе (поддельный архив из {@link IndexedCoreIntegrationTest}).
 * Отдельный контекст: закреплённые репозитории живут в синглтоне и не должны влиять на другие тесты.
 */
@SpringBootTest(properties = {
        "app.archive.mode=http",
        "app.index.enabled=true",
        "app.index.build-on-startup=false",
        "app.index.window-hours=3",
        "app.index.top-n=2",
        "app.index.pins.max-pinned=5",
        "app.archive.default-window-hours=3",
        "app.github.history.existence-check=false"
})
@AutoConfigureMockMvc
@Import(IndexedCoreIntegrationTest.FakeArchiveConfig.class)
class IndexAdmissionIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RepoIndexBuilder builder;

    @Autowired
    private RepoIndex repoIndex;

    @MockitoBean
    private ScanService scanService;

    @BeforeEach
    void buildIndex() {
        repoIndex.swap(builder.build());
    }

    @Test
    void pinnedLongTailRepositoryIsPromotedAndSurvivesRebuild() throws Exception {
        // a/two вне топ-2, но со звездой в окне: сразу в топ, отчёт 200
        mockMvc.perform(put("/api/v1/index/repos/a/two").param("hours", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stats.stars").value(1))
                .andExpect(jsonPath("$.source.tier").value("top"));
        mockMvc.perform(put("/api/v1/index/repos/A/Two"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/index/repos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.top_n").value(3))
                .andExpect(jsonPath("$.repos[?(@.full_name == 'a/two')].pinned", hasItem(true)))
                .andExpect(jsonPath("$.repos[?(@.full_name == 'a/one')].pinned", hasItem(false)));

        repoIndex.swap(builder.build());
        mockMvc.perform(get("/api/v1/repos/a/two").param("hours", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.source.tier").value("top"));
    }

    @Test
    void repositoryWithoutEventsInWindowGets202AndScan() throws Exception {
        ScanJob job = mock(ScanJob.class);
        when(job.id()).thenReturn("scan-7");
        when(job.repo()).thenReturn(new RepoRef("nobody", "silent"));
        when(job.status()).thenReturn(ScanJob.Status.QUEUED);
        when(job.source()).thenReturn(ScanJob.Source.ARCHIVE);
        when(job.createdAt()).thenReturn(Instant.parse("2026-11-11T12:00:00Z"));
        when(scanService.start(any(RepoRef.class), anyInt())).thenReturn(job);

        mockMvc.perform(post("/api/v1/index/repos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"owner\":\"nobody\",\"name\":\"silent\"}"))
                .andExpect(status().isAccepted())
                .andExpect(header().string("Location", "/api/v1/scans/scan-7"))
                .andExpect(jsonPath("$.id").value("scan-7"))
                .andExpect(jsonPath("$.repo.full_name").value("nobody/silent"));
        // Событий в окне индекса нет — сканируем шире: max-window-hours (24 по умолчанию).
        verify(scanService).start(new RepoRef("nobody", "silent"), 24);
    }
}
