package ru.itmo.ipkn.starguard.core.metrics;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** X-2: метрики Star Guard видны в /actuator/prometheus. */
@SpringBootTest(properties = "app.archive.mode=fixture")
@AutoConfigureMockMvc
class PrometheusEndpointTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void exposesStarGuardMetrics() throws Exception {
        mockMvc.perform(post("/api/v1/scans")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"owner\": \"demo\", \"name\": \"star-guard\", \"hours\": 3}"))
                .andExpect(status().isAccepted());

        String body = "";
        for (int i = 0; i < 100 && !body.contains("starguard_scan_latency_seconds_count"); i++) {
            Thread.sleep(50);
            body = mockMvc.perform(get("/actuator/prometheus"))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();
        }

        assertThat(body)
                .contains("starguard_scan_latency_seconds_count")
                .contains("source=\"archive\"")
                .contains("starguard_scan_active")
                .contains("starguard_index_size")
                .contains("starguard_index_memory_budget_bytes")
                .contains("application=\"star-guard\"");
    }
}
