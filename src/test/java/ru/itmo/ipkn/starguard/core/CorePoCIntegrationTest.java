package ru.itmo.ipkn.starguard.core;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "app.archive.mode=fixture")
@AutoConfigureMockMvc
class CorePoCIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void returnsStatsAndSuspiciousMockScore() throws Exception {
        mockMvc.perform(get("/api/v1/repos/demo/star-guard")
                        .param("hours", "3")
                        .param("mock_mode", "suspicious"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.repo.full_name").value("demo/star-guard"))
                .andExpect(jsonPath("$.window.hours").value(3))
                .andExpect(jsonPath("$.stats.total_events").value(173))
                .andExpect(jsonPath("$.stats.stars").value(45))
                .andExpect(jsonPath("$.stats.forks").value(12))
                .andExpect(jsonPath("$.stats.pushes").value(70))
                .andExpect(jsonPath("$.stats.other").value(6))
                .andExpect(jsonPath("$.stats.peak_stars_10m").value(9))
                .andExpect(jsonPath("$.stats.stars_by_hour").isArray())
                .andExpect(jsonPath("$.score.mocked").value(true))
                .andExpect(jsonPath("$.score.level").value("critical"))
                .andExpect(jsonPath("$.score.score").value(86))
                .andExpect(jsonPath("$.score.checks.length()").value(5))
                .andExpect(jsonPath("$.score.rules_applied").isArray())
                .andExpect(jsonPath("$.archive.hours_requested").value(3))
                .andExpect(jsonPath("$.archive.hours_loaded").value(3))
                .andExpect(jsonPath("$.archive.parse_errors").value(0));
    }

    @Test
    void usesHealthyProfileByDefault() throws Exception {
        mockMvc.perform(get("/api/v1/repos/demo/star-guard").param("hours", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.score.level").value("low"))
                .andExpect(jsonPath("$.score.score").value(10));
    }

    @Test
    void returns404ForUnknownRepository() throws Exception {
        mockMvc.perform(get("/api/v1/repos/demo/unknown").param("hours", "3"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("REPO_NOT_FOUND_IN_WINDOW"));
    }

    @Test
    void returns400ForInvalidMockMode() throws Exception {
        mockMvc.perform(get("/api/v1/repos/demo/star-guard").param("mock_mode", "nope"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void returns400ForInvalidHours() throws Exception {
        mockMvc.perform(get("/api/v1/repos/demo/star-guard").param("hours", "0"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void exposesOpenApiDocument() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.openapi").exists())
                .andExpect(content().string(containsString("/api/v1/repos/{owner}/{name}")));
    }
}
