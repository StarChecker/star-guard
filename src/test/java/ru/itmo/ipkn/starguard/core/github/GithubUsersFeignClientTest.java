package ru.itmo.ipkn.starguard.core.github;

import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import ru.itmo.ipkn.starguard.core.archive.GhArchiveFeignClient;

import java.time.Instant;

import static com.github.tomakehurst.wiremock.client.WireMock.absent;
import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {"app.index.enabled=false", "app.github.token=test-token"})
class GithubUsersFeignClientTest {

    private static final WireMockServer WIRE_MOCK = new WireMockServer(options().dynamicPort());

    static {
        WIRE_MOCK.start();
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("app.github.base-url", () -> "http://127.0.0.1:" + WIRE_MOCK.port());
        registry.add("app.archive.base-url", () -> "http://127.0.0.1:" + WIRE_MOCK.port());
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
    private GithubUsersFeignClient client;

    @Autowired
    private GhArchiveFeignClient archiveClient;

    @Test
    void decodesCreatedAtAndSendsGithubHeaders() {
        WIRE_MOCK.stubFor(get(urlEqualTo("/users/octocat"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"login":"octocat","id":583231,"created_at":"2011-01-25T18:44:36Z"}
                                """)));

        GithubUser user = client.user("octocat");

        assertThat(user.login()).isEqualTo("octocat");
        assertThat(user.createdAt()).isEqualTo(Instant.parse("2011-01-25T18:44:36Z"));
        WIRE_MOCK.verify(getRequestedFor(urlEqualTo("/users/octocat"))
                .withHeader("Authorization", equalTo("Bearer test-token"))
                .withHeader("Accept", equalTo("application/vnd.github+json"))
                .withHeader("X-GitHub-Api-Version", equalTo("2022-11-28"))
                .withHeader("User-Agent", equalTo("star-guard")));
    }

    @Test
    void doesNotLeakGithubTokenToArchiveClient() {
        WIRE_MOCK.stubFor(get(urlEqualTo("/2026-11-09-0.json.gz"))
                .willReturn(aResponse().withStatus(200).withBody(new byte[0])));

        archiveClient.download("2026-11-09-0.json.gz").close();

        WIRE_MOCK.verify(getRequestedFor(urlEqualTo("/2026-11-09-0.json.gz"))
                .withHeader("Authorization", absent()));
    }
}
