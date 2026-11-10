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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Реальный C3 на фикстурах demo/star-stuffing и demo/healthy-lib.
 * Включается общим флагом {@code app.scoring.mode=real}: так заодно проверяется,
 * что {@code app.scoring.checks.engagement.mode} наследует его через плейсхолдер.
 */
@SpringBootTest(properties = {"app.archive.mode=fixture", "app.scoring.mode=real"})
@AutoConfigureMockMvc
class EngagementCheckIntegrationTest {

    /** Индекс C3 в explain: порядок проверок фиксирован {@link CheckIds#ALL}. */
    private static final String C3 = "$.score.checks[2]";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ApplicationContext context;

    @Test
    void globalRealModeReplacesMockWithRealCheck() {
        assertThat(context.getBeansOfType(EngagementCheck.class)).hasSize(1);
        assertThat(context.getBeansOfType(MockEngagementCheck.class)).isEmpty();
    }

    @Test
    void starStuffingFixtureTriggersEngagementCheck() throws Exception {
        mockMvc.perform(get("/api/v1/repos/demo/star-stuffing").param("hours", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stats.stars").value(120))
                .andExpect(jsonPath(C3 + ".id").value(CheckIds.ENGAGEMENT))
                .andExpect(jsonPath(C3 + ".score", greaterThanOrEqualTo(0.9)))
                .andExpect(jsonPath(C3 + ".active").value(true))
                .andExpect(jsonPath(C3 + ".details.mocked").value(false))
                .andExpect(jsonPath(C3 + ".details.activity").value(2));
    }

    @Test
    void healthyLibFixtureKeepsEngagementLow() throws Exception {
        mockMvc.perform(get("/api/v1/repos/demo/healthy-lib").param("hours", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stats.stars").value(30))
                .andExpect(jsonPath(C3 + ".id").value(CheckIds.ENGAGEMENT))
                .andExpect(jsonPath(C3 + ".score", lessThanOrEqualTo(0.2)))
                .andExpect(jsonPath(C3 + ".active").value(true))
                .andExpect(jsonPath(C3 + ".details.mocked").value(false));
    }

    @Test
    void existingDemoFixtureIsUnaffectedByNewFixtures() throws Exception {
        mockMvc.perform(get("/api/v1/repos/demo/star-guard").param("hours", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stats.total_events").value(173))
                .andExpect(jsonPath("$.archive.parse_errors").value(0))
                .andExpect(jsonPath(C3 + ".score", lessThanOrEqualTo(0.2)));
    }
}
