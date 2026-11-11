package ru.itmo.ipkn.starguard.core.github;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.time.Duration;
import java.time.Instant;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "app.archive.mode=fixture",
        "app.scoring.checks.fresh-accounts.mode=real",
        "app.github.enabled=true"})
@AutoConfigureMockMvc
class AccountAgeEnrichmentIntegrationTest {

    private static final String C2 = "$.score.checks[1]";
    private static final WireMockServer WIRE_MOCK = new WireMockServer(options().dynamicPort());

    static {
        WIRE_MOCK.start();
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("app.github.base-url", () -> "http://127.0.0.1:" + WIRE_MOCK.port());
    }

    @AfterAll
    static void stopWireMock() {
        WIRE_MOCK.stop();
    }

    @BeforeEach
    void resetWireMock() {
        WIRE_MOCK.resetAll();
    }

    @Autowired
    private MockMvc mockMvc;

    @Test
    void githubAccountDatesActivateFreshAccountsCheck() throws Exception {
        Instant created = Instant.now().minus(Duration.ofDays(5));
        WIRE_MOCK.stubFor(WireMock.get(urlPathMatching("/users/star-acc-.*"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"login\":\"star-acc\",\"created_at\":\"" + created + "\"}")));

        mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/repos/demo/no-actor-data").param("hours", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(C2 + ".active").value(true))
                .andExpect(jsonPath(C2 + ".confidence").value(0.9))
                .andExpect(jsonPath(C2 + ".score", greaterThanOrEqualTo(0.9)))
                .andExpect(jsonPath(C2 + ".details.actors_with_data").value(25))
                .andExpect(jsonPath(C2 + ".details.new_accounts").value(25));

        WIRE_MOCK.verify(25, getRequestedFor(urlPathMatching("/users/star-acc-.*")));
    }
}
