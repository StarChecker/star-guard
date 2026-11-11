package ru.itmo.ipkn.starguard.core.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import ru.itmo.ipkn.starguard.core.domain.EventCounts;
import ru.itmo.ipkn.starguard.core.github.RepoExistenceService;
import ru.itmo.ipkn.starguard.core.domain.RepoRef;
import ru.itmo.ipkn.starguard.core.domain.TimeWindow;
import ru.itmo.ipkn.starguard.core.index.IndexedRepo;
import ru.itmo.ipkn.starguard.core.index.RepoHourAggregate;
import ru.itmo.ipkn.starguard.core.index.RepoIndex;
import ru.itmo.ipkn.starguard.core.index.RepoIndexSnapshot;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(IndexController.class)
@Import(ApiExceptionHandler.class)
class IndexControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RepoIndex repoIndex;

    @MockitoBean
    private RepoExistenceService existence;

    @Test
    void returnsTopRepositoriesWithRanks() throws Exception {
        when(repoIndex.snapshot()).thenReturn(Optional.of(snapshot()));

        mockMvc.perform(get("/api/v1/index/repos").param("limit", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.window.hours").value(1))
                .andExpect(jsonPath("$.top_n").value(2))
                .andExpect(jsonPath("$.hours_loaded").value(1))
                .andExpect(jsonPath("$.repos.length()").value(1))
                .andExpect(jsonPath("$.repos[0].rank").value(1))
                .andExpect(jsonPath("$.repos[0].full_name").value("a/one"))
                .andExpect(jsonPath("$.repos[0].stars").value(5));
    }

    @Test
    void skipsRepositoriesDeletedOnGithub() throws Exception {
        when(repoIndex.snapshot()).thenReturn(Optional.of(snapshot()));
        when(existence.enabled()).thenReturn(true);
        when(existence.check(anyList())).thenReturn(Map.of("a/one", false, "b/two", true));

        mockMvc.perform(get("/api/v1/index/repos").param("limit", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.skipped_deleted").value(1))
                .andExpect(jsonPath("$.repos.length()").value(1))
                .andExpect(jsonPath("$.repos[0].full_name").value("b/two"))
                .andExpect(jsonPath("$.repos[0].rank").value(2))
                .andExpect(jsonPath("$.repos[0].exists").value(true));

        mockMvc.perform(get("/api/v1/index/repos").param("limit", "5").param("existing_only", "false"))
                .andExpect(jsonPath("$.repos.length()").value(2))
                .andExpect(jsonPath("$.skipped_deleted").value(0));
    }

    @Test
    void returns503WhenIndexIsWarmingUp() throws Exception {
        when(repoIndex.snapshot()).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/index/repos"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("INDEX_NOT_READY"));
    }

    private static RepoIndexSnapshot snapshot() {
        TimeWindow window = new TimeWindow(
                Instant.parse("2026-11-10T09:00:00Z"),
                Instant.parse("2026-11-10T10:00:00Z"),
                1);
        Instant hour = window.from();
        EventCounts fiveStars = new EventCounts(5, 5, 0, 0, 0, 0, 0);
        EventCounts twoStars = new EventCounts(2, 2, 0, 0, 0, 0, 0);
        IndexedRepo one = new IndexedRepo(
                new RepoRef("a", "one"),
                List.of(new RepoHourAggregate(hour, fiveStars, new int[60], 5, 5)));
        IndexedRepo two = new IndexedRepo(
                new RepoRef("b", "two"),
                List.of(new RepoHourAggregate(hour, twoStars, new int[60], 2, 2)));
        return new RepoIndexSnapshot(
                window, Instant.parse("2026-11-10T12:00:00Z"), 1, List.of(), 0,
                Map.of("a/one", one, "b/two", two));
    }
}
