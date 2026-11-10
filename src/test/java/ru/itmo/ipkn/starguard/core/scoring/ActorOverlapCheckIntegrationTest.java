package ru.itmo.ipkn.starguard.core.scoring;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Реальный C4 на фикстуре demo-star-farm: ферма из 30 аккаунтов ставит звёзды
 * demo/farm-target и ещё четырём репозиториям; demo/organic-app — живые звёзды
 * с единичными пересечениями. Остальные проверки остаются моками.
 */
@SpringBootTest(properties = {
        "app.archive.mode=fixture",
        "app.scoring.checks.actor-overlap.mode=real"})
@AutoConfigureMockMvc
class ActorOverlapCheckIntegrationTest {

    /** Индекс C4 в explain: порядок проверок фиксирован {@link CheckIds#ALL}. */
    private static final String C4 = "$.score.checks[3]";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ApplicationContext context;

    @Test
    void pointRealModeReplacesOnlyActorOverlapMock() {
        assertThat(context.getBeansOfType(ActorOverlapCheck.class)).hasSize(1);
        assertThat(context.getBeansOfType(MockActorOverlapCheck.class)).isEmpty();
        assertThat(context.getBeansOfType(MockEngagementCheck.class)).hasSize(1);
    }

    @Test
    void farmFixtureTriggersActorOverlap() throws Exception {
        mockMvc.perform(get("/api/v1/repos/demo/farm-target").param("hours", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stats.stars").value(32))
                .andExpect(jsonPath(C4 + ".id").value(CheckIds.ACTOR_OVERLAP))
                .andExpect(jsonPath(C4 + ".score", greaterThanOrEqualTo(0.9)))
                .andExpect(jsonPath(C4 + ".active").value(true))
                .andExpect(jsonPath(C4 + ".details.mocked").value(false))
                .andExpect(jsonPath(C4 + ".details.star_actors").value(32))
                .andExpect(jsonPath(C4 + ".details.multi_repo_actors").value(30))
                .andExpect(jsonPath(C4 + ".details.top_overlaps[0].repo", startsWith("demo/farm-")))
                .andExpect(jsonPath(C4 + ".details.top_overlaps[0].shared_actors").value(30))
                .andExpect(jsonPath("$.score.rules_applied[0]").value("strong_check"));
    }

    @Test
    void organicFixtureKeepsActorOverlapLow() throws Exception {
        mockMvc.perform(get("/api/v1/repos/demo/organic-app").param("hours", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stats.stars").value(25))
                .andExpect(jsonPath(C4 + ".id").value(CheckIds.ACTOR_OVERLAP))
                .andExpect(jsonPath(C4 + ".score", lessThanOrEqualTo(0.1)))
                .andExpect(jsonPath(C4 + ".active").value(true))
                .andExpect(jsonPath(C4 + ".details.overlapping_repos").value(2));
    }

    @Test
    void repoWithoutCrossStarsStaysLow() throws Exception {
        mockMvc.perform(get("/api/v1/repos/demo/star-guard").param("hours", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stats.total_events").value(173))
                .andExpect(jsonPath(C4 + ".score", lessThanOrEqualTo(0.1)))
                .andExpect(jsonPath(C4 + ".details.overlapping_repos").value(0));
    }
}
