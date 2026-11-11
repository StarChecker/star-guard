package ru.itmo.ipkn.starguard.core.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * API под расширение на фикстурах с реальными C2–C4: тексты explain, таймлайн звёзд
 * с разметкой подозрительных, пакетные сводки, бейдж и скан по запросу.
 */
@SpringBootTest(properties = {
        "app.archive.mode=fixture",
        "app.scoring.checks.actor-overlap.mode=real",
        "app.scoring.checks.fresh-accounts.mode=real",
        "app.scoring.checks.engagement.mode=real"})
@AutoConfigureMockMvc
class UxApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void reportCarriesVerdictSummariesAndTimeline() throws Exception {
        mockMvc.perform(get("/api/v1/repos/demo/farm-target").param("hours", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.score.verdict", startsWith("")))
                .andExpect(jsonPath("$.score.checks[3].title").value("Пересечение аккаунтов"))
                .andExpect(jsonPath("$.score.checks[3].summary", containsString("30 из 32")))
                .andExpect(jsonPath("$.score.checks[0].summary", containsString("Демо-значение")))
                .andExpect(jsonPath("$.stars_timeline.bucket_minutes").value(10))
                .andExpect(jsonPath("$.stars_timeline.estimated").value(false))
                .andExpect(jsonPath("$.stars_timeline.points", hasSize(18)))
                .andExpect(jsonPath("$.stars_timeline.suspicious_stars").value(30))
                .andExpect(jsonPath("$.stars_timeline.suspicious_by_check.actor_overlap").value(30));
    }

    @Test
    void organicRepoHasNoSuspiciousStars() throws Exception {
        mockMvc.perform(get("/api/v1/repos/demo/organic-app").param("hours", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stars_timeline.suspicious_stars").value(0))
                .andExpect(jsonPath("$.score.verdict").value("Признаков накрутки не найдено."));
    }

    @Test
    void freshFarmStarsAreMarkedByFreshAccounts() throws Exception {
        mockMvc.perform(get("/api/v1/repos/demo/fresh-farm").param("hours", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stars_timeline.suspicious_by_check.fresh_accounts",
                        greaterThanOrEqualTo(20)))
                .andExpect(jsonPath("$.score.checks[1].summary", containsString("моложе 30 дн.")));
    }

    @Test
    void batchReturnsSummariesAndStatuses() throws Exception {
        mockMvc.perform(post("/api/v1/repos/batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"repos": ["demo/farm-target", "demo/organic-app", "nobody/nothing"], "hours": 3}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[0].full_name").value("demo/farm-target"))
                .andExpect(jsonPath("$[0].status").value("ok"))
                .andExpect(jsonPath("$[0].suspicious_stars").value(30))
                .andExpect(jsonPath("$[1].level").value("low"))
                .andExpect(jsonPath("$[2].status").value("no_data"))
                .andExpect(jsonPath("$[2].score").doesNotExist());
    }

    @Test
    void batchValidatesInput() throws Exception {
        mockMvc.perform(post("/api/v1/repos/batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"repos\": [\"not a repo\"]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        mockMvc.perform(post("/api/v1/repos/batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"repos\": []}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void badgeFollowsShieldsEndpointFormat() throws Exception {
        mockMvc.perform(get("/api/v1/badge/demo/organic-app").param("hours", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.schemaVersion").value(1))
                .andExpect(jsonPath("$.label").value("Star Guard"))
                .andExpect(jsonPath("$.message", startsWith("no signs of fake stars")))
                .andExpect(jsonPath("$.color").value("2ea44f"))
                .andExpect(jsonPath("$.cacheSeconds").value(1800));
        mockMvc.perform(get("/api/v1/badge/nobody/nothing").param("hours", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("no recent stars"))
                .andExpect(jsonPath("$.color").value("lightgrey"));
    }

    @Test
    void scanRunsInBackgroundAndReturnsReport() throws Exception {
        MvcResult started = mockMvc.perform(post("/api/v1/scans")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"owner\": \"demo\", \"name\": \"farm-target\", \"hours\": 3}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.hours_total").value(3))
                .andReturn();
        String id = com.jayway.jsonpath.JsonPath.read(started.getResponse().getContentAsString(), "$.id");

        String state = "queued";
        for (int i = 0; i < 100 && !state.equals("done") && !state.equals("failed"); i++) {
            Thread.sleep(50);
            MvcResult polled = mockMvc.perform(get("/api/v1/scans/" + id)).andReturn();
            state = com.jayway.jsonpath.JsonPath.read(polled.getResponse().getContentAsString(), "$.status");
        }
        assertThat(state).isEqualTo("done");
        mockMvc.perform(get("/api/v1/scans/" + id))
                .andExpect(jsonPath("$.hours_done").value(3))
                .andExpect(jsonPath("$.report.repo.full_name").value("demo/farm-target"))
                .andExpect(jsonPath("$.report.stars_timeline.suspicious_stars").value(30));

        // повторный запуск того же скана возвращает тот же id
        mockMvc.perform(post("/api/v1/scans")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"owner\": \"demo\", \"name\": \"farm-target\", \"hours\": 3}"))
                .andExpect(jsonPath("$.id").value(id));
    }

    @Test
    void scanOfUnknownRepoFailsWithCode() throws Exception {
        MvcResult started = mockMvc.perform(post("/api/v1/scans")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"owner\": \"nobody\", \"name\": \"nothing\", \"hours\": 3}"))
                .andExpect(status().isAccepted())
                .andReturn();
        String id = com.jayway.jsonpath.JsonPath.read(started.getResponse().getContentAsString(), "$.id");
        String state = "queued";
        for (int i = 0; i < 100 && !state.equals("done") && !state.equals("failed"); i++) {
            Thread.sleep(50);
            state = com.jayway.jsonpath.JsonPath.read(
                    mockMvc.perform(get("/api/v1/scans/" + id)).andReturn().getResponse().getContentAsString(),
                    "$.status");
        }
        assertThat(state).isEqualTo("failed");
        mockMvc.perform(get("/api/v1/scans/" + id))
                .andExpect(jsonPath("$.error_code").value("REPO_NOT_FOUND_IN_WINDOW"));
        mockMvc.perform(get("/api/v1/scans/does-not-exist"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SCAN_NOT_FOUND"));
    }
}
