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

/** Проверяет реальный C1 на событиях GH Archive и его детали в REST explain. */
@SpringBootTest(properties = {"app.archive.mode=fixture", "app.scoring.checks.burst.mode=real"})
@AutoConfigureMockMvc
class BurstCheckIntegrationTest {

    private static final String C1 = "$.score.checks[0]";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ApplicationContext context;

    @Test
    void registersRealCheckWithoutMock() {
        assertThat(context.getBeansOfType(BurstCheck.class)).hasSize(1);
        assertThat(context.getBeansOfType(MockBurstCheck.class)).isEmpty();
    }

    @Test
    void burstDetailsAreVisibleInExplain() throws Exception {
        mockMvc.perform(get("/api/v1/repos/demo/star-stuffing").param("hours", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(C1 + ".id").value(CheckIds.BURST))
                .andExpect(jsonPath(C1 + ".score").value(greaterThanOrEqualTo(0.9), Double.class))
                .andExpect(jsonPath(C1 + ".active").value(true))
                .andExpect(jsonPath(C1 + ".details.mocked").value(false))
                .andExpect(jsonPath(C1 + ".details.window_start").isString())
                .andExpect(jsonPath(C1 + ".details.max_burst_10m", greaterThanOrEqualTo(10)))
                .andExpect(jsonPath(C1 + ".details.ratio").value(greaterThanOrEqualTo(15.0), Double.class));
    }

    @Test
    void healthyFixtureHasLowBurstScore() throws Exception {
        mockMvc.perform(get("/api/v1/repos/demo/healthy-lib").param("hours", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(C1 + ".score").value(lessThanOrEqualTo(0.2), Double.class));
    }
}
