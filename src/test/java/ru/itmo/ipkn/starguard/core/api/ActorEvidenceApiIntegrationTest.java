package ru.itmo.ipkn.starguard.core.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "app.archive.mode=fixture",
        "app.scoring.checks.burst.mode=real",
        "app.scoring.checks.fresh-accounts.mode=real",
        "app.scoring.checks.actor-overlap.mode=real",
        "app.scoring.checks.engagement.mode=real",
        "app.api.actor-logins=hash",
        "app.api.suspicious-actors-limit=5"})
@AutoConfigureMockMvc
class ActorEvidenceApiIntegrationTest {
    @Autowired
    private MockMvc mvc;

    @Test
    void realBurstAndLimitedPrivateActorsReachTheApi() throws Exception {
        mvc.perform(get("/api/v1/repos/demo/farm-target").param("hours", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stars_timeline.spike_intervals", hasSize(1)))
                .andExpect(jsonPath("$.stars_timeline.spike_intervals[0].suspicious").value(true))
                .andExpect(jsonPath("$.stars_timeline.spike_intervals[0].stars", greaterThanOrEqualTo(20)))
                .andExpect(jsonPath("$.stars_timeline.spike_intervals[0].from", notNullValue()))
                .andExpect(jsonPath("$.stars_timeline.spike_intervals[0].to", notNullValue()))
                .andExpect(jsonPath("$.stars_timeline.spike_intervals[0].ratio", greaterThan(3.0)))
                .andExpect(jsonPath("$.stars_timeline.spike_intervals[0].peak", greaterThan(0)))
                .andExpect(jsonPath("$.suspicious_actors", hasSize(5)))
                .andExpect(jsonPath("$.suspicious_actors_total").value(30))
                .andExpect(jsonPath("$.suspicious_actors[*].login", everyItem(nullValue())))
                .andExpect(jsonPath("$.suspicious_actors[0].actor_hash", matchesPattern("[0-9a-f]{12}")))
                .andExpect(jsonPath("$.suspicious_actors[0].reasons", contains("overlap")))
                .andExpect(jsonPath("$.suspicious_actors[0].shared_repos", greaterThanOrEqualTo(4)))
                .andExpect(jsonPath("$.stars_timeline.suspicious_stars").value(30))
                .andExpect(jsonPath("$.stats.contributors").value(1));
    }

    @Test
    void organicRepoRemainsNeutralWithRealChecks() throws Exception {
        mvc.perform(get("/api/v1/repos/demo/organic-app").param("hours", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stars_timeline.spike_intervals", empty()))
                .andExpect(jsonPath("$.suspicious_actors", empty()))
                .andExpect(jsonPath("$.suspicious_actors_total").value(0))
                .andExpect(jsonPath("$.stats.contributors").value(3));
    }

    @Test
    void openApiContainsNewReportFields() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components.schemas.RepoReportDto.properties.suspicious_actors").exists())
                .andExpect(jsonPath("$.components.schemas.StatsDto.properties.contributors").exists())
                .andExpect(jsonPath("$.components.schemas.StarTimelineDto.properties.spike_intervals").exists());
    }
}
