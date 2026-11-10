package ru.itmo.ipkn.starguard.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.web.servlet.MockMvc;
import ru.itmo.ipkn.starguard.core.archive.GhArchiveBulkReader;
import ru.itmo.ipkn.starguard.core.archive.HourReadResult;
import ru.itmo.ipkn.starguard.core.domain.EventType;
import ru.itmo.ipkn.starguard.core.domain.GhEvent;
import ru.itmo.ipkn.starguard.core.index.RepoIndex;
import ru.itmo.ipkn.starguard.core.index.RepoIndexBuilder;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "app.archive.mode=http",
        "app.index.enabled=true",
        "app.index.build-on-startup=false",
        "app.index.window-hours=3",
        "app.index.top-n=2",
        "app.archive.default-window-hours=3"
})
@AutoConfigureMockMvc
class IndexedCoreIntegrationTest {

    private static final Instant H07 = Instant.parse("2026-11-10T07:00:00Z");
    private static final Instant H08 = Instant.parse("2026-11-10T08:00:00Z");
    private static final Instant H09 = Instant.parse("2026-11-10T09:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RepoIndexBuilder builder;

    @Autowired
    private RepoIndex repoIndex;

    @BeforeEach
    void buildIndex() {
        repoIndex.swap(builder.build());
    }

    @Test
    void servesIndexedRepositoryFromPrecomputedAggregates() throws Exception {
        mockMvc.perform(get("/api/v1/repos/a/one").param("hours", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.repo.full_name").value("a/one"))
                .andExpect(jsonPath("$.window.hours").value(2))
                .andExpect(jsonPath("$.stats.stars").value(3))
                .andExpect(jsonPath("$.archive.hours_loaded").value(2))
                .andExpect(jsonPath("$.archive.cached").value(true))
                .andExpect(jsonPath("$.score.mocked").value(true));
    }

    @Test
    void returnsFast404ForRepositoryOutsideIndex() throws Exception {
        mockMvc.perform(get("/api/v1/repos/a/two"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("REPO_NOT_INDEXED"));
    }

    @Test
    void exposesIndexedTopRepositories() throws Exception {
        mockMvc.perform(get("/api/v1/index/repos").param("limit", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.top_n").value(2))
                .andExpect(jsonPath("$.repos.length()").value(2))
                .andExpect(jsonPath("$.repos[0].full_name").value("a/one"))
                .andExpect(jsonPath("$.repos[0].stars").value(5))
                .andExpect(jsonPath("$.repos[1].full_name").value("b/three"));
    }

    @TestConfiguration
    static class FakeArchiveConfig {

        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(Instant.parse("2026-11-10T12:00:00Z"), ZoneOffset.UTC);
        }

        @Bean
        @Primary
        GhArchiveBulkReader fakeBulkReader() {
            Map<Instant, List<GhEvent>> events = new HashMap<>();
            add(events, H07, event("1", EventType.WATCH, "a/one", 1, H07, 10));
            add(events, H07, event("2", EventType.WATCH, "a/one", 2, H07, 20));
            add(events, H07, event("3", EventType.WATCH, "b/three", 3, H07, 30));
            add(events, H07, event("4", EventType.WATCH, "c/four", 4, H07, 40));
            add(events, H08, event("5", EventType.WATCH, "a/one", 1, H08, 10));
            add(events, H08, event("6", EventType.WATCH, "a/one", 2, H08, 20));
            add(events, H08, event("7", EventType.WATCH, "b/three", 3, H08, 30));
            add(events, H09, event("8", EventType.WATCH, "a/one", 1, H09, 10));
            add(events, H09, event("9", EventType.WATCH, "a/two", 5, H09, 20));
            add(events, H09, event("10", EventType.PUSH, "a/one", 6, H09, 30));

            return (hour, sink) -> {
                events.getOrDefault(hour, List.of()).forEach(sink);
                return new HourReadResult(hour, 0, false, false);
            };
        }

        private static void add(
                Map<Instant, List<GhEvent>> events, Instant hour, GhEvent event) {
            events.computeIfAbsent(hour, ignored -> new ArrayList<>()).add(event);
        }

        private static GhEvent event(
                String id, EventType type, String repo, long actor, Instant hour, int minute) {
            return new GhEvent(id, type, 1L, repo, actor, "dev" + actor,
                    hour.plus(Duration.ofMinutes(minute)));
        }
    }
}
