package ru.itmo.ipkn.starguard.core.scoring;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.lessThan;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {"app.archive.mode=fixture", "app.scoring.checks.fresh-accounts.mode=real"})
@AutoConfigureMockMvc
class FreshAccountsCheckIntegrationTest {

    private static final String C2 = "$.score.checks[1]";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ApplicationContext context;

    @Test
    void realModeReplacesMockWithRealCheck() {
        assertThat(context.getBeansOfType(FreshAccountsCheck.class)).hasSize(1);
        assertThat(context.getBeansOfType(MockFreshAccountsCheck.class)).isEmpty();
    }

    @Test
    void freshFarmFixtureTriggersCheckAndStrongCheckRule() throws Exception {
        mockMvc.perform(get("/api/v1/repos/demo/fresh-farm").param("hours", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stats.stars").value(25))
                .andExpect(jsonPath(C2 + ".id").value(CheckIds.FRESH_ACCOUNTS))
                .andExpect(jsonPath(C2 + ".score", greaterThanOrEqualTo(0.9)))
                .andExpect(jsonPath(C2 + ".confidence").value(0.9))
                .andExpect(jsonPath(C2 + ".active").value(true))
                .andExpect(jsonPath(C2 + ".details.mocked").value(false))
                .andExpect(jsonPath(C2 + ".details.star_actors").value(25))
                .andExpect(jsonPath(C2 + ".details.fresh_actors").value(22))
                .andExpect(jsonPath(C2 + ".details.fresh_actor_sample.length()").value(5))
                .andExpect(jsonPath("$.score.rules_applied[0]").value("strong_check"))
                .andExpect(jsonPath("$.score.level").value("high"));
    }

    @Test
    void organicFixtureKeepsCheckLow() throws Exception {
        mockMvc.perform(get("/api/v1/repos/demo/organic").param("hours", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stats.stars").value(30))
                .andExpect(jsonPath(C2 + ".score", lessThanOrEqualTo(0.1)))
                .andExpect(jsonPath(C2 + ".active").value(true))
                .andExpect(jsonPath(C2 + ".details.fresh_share").value(0.1));
    }

    @Test
    void fixtureWithoutActorDataLeavesCheckInactive() throws Exception {
        mockMvc.perform(get("/api/v1/repos/demo/no-actor-data").param("hours", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stats.stars").value(25))
                .andExpect(jsonPath(C2 + ".confidence", lessThan(0.5)))
                .andExpect(jsonPath(C2 + ".score").value(0.0))
                .andExpect(jsonPath(C2 + ".active").value(false))
                .andExpect(jsonPath(C2 + ".contribution").value(0.0))
                .andExpect(jsonPath(C2 + ".details.reason").value("no_actor_data"));
    }

    @Test
    void existingDemoFixtureIsUnaffectedByNewFixtures() throws Exception {
        mockMvc.perform(get("/api/v1/repos/demo/star-guard").param("hours", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stats.total_events").value(173))
                .andExpect(jsonPath("$.archive.parse_errors").value(0))
                .andExpect(jsonPath(C2 + ".active").value(false));
    }
}
