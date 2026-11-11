package ru.itmo.ipkn.starguard.core.github;

import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import ru.itmo.ipkn.starguard.core.config.GithubPreviewProperties;
import ru.itmo.ipkn.starguard.core.config.GithubProperties;
import ru.itmo.ipkn.starguard.core.domain.RepoRef;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * F5: preview по GitHub REST через WireMock — карточка репозитория, контрибьюторы по Link,
 * кэш, удалённые репозитории, лимит GitHub и эндпоинт {@code /api/v1/preview}.
 */
@SpringBootTest(properties = {"app.index.enabled=false", "app.github.token=test-token"})
@AutoConfigureMockMvc
class GithubPreviewIntegrationTest {

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
    private GithubPreviewService previewService;

    @Autowired
    private GithubReposFeignClient client;

    @Autowired
    private MockMvc mockMvc;

    @Test
    void returnsRepositoryStatsAndCountsContributorsFromLinkHeader() {
        stubRepo("acme", "tool", "acme/tool");
        WIRE_MOCK.stubFor(get(urlPathEqualTo("/repos/acme/tool/contributors"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withHeader("Link", "<https://api.github.com/repositories/1/contributors?per_page=1&anon=1&page=2>; rel=\"next\", "
                                + "<https://api.github.com/repositories/1/contributors?per_page=1&anon=1&page=57>; rel=\"last\"")
                        .withBody("[{\"login\":\"dev\"}]")));

        RepoPreview first = previewService.preview(new RepoRef("acme", "tool"));
        RepoPreview second = previewService.preview(new RepoRef("ACME", "Tool"));

        assertThat(first.stars()).isEqualTo(2650);
        assertThat(first.forks()).isEqualTo(240);
        assertThat(first.openIssues()).isEqualTo(12);
        assertThat(first.watchers()).isEqualTo(80);
        assertThat(first.contributors()).isEqualTo(57);
        assertThat(first.createdAt()).isEqualTo(Instant.parse("2020-01-02T03:04:05Z"));
        assertThat(first.cached()).isFalse();
        assertThat(second.cached()).isTrue();
        WIRE_MOCK.verify(1, getRequestedFor(urlEqualTo("/repos/acme/tool")));
        WIRE_MOCK.verify(getRequestedFor(urlPathEqualTo("/repos/acme/tool/contributors"))
                .withQueryParam("per_page", equalTo("1"))
                .withQueryParam("anon", equalTo("1"))
                .withHeader("Authorization", equalTo("Bearer test-token")));
    }

    @Test
    void contributorsAreOptional() {
        stubRepo("big", "monorepo", "big/monorepo");
        WIRE_MOCK.stubFor(get(urlPathEqualTo("/repos/big/monorepo/contributors"))
                .willReturn(aResponse().withStatus(403).withHeader("Content-Type", "application/json")
                        .withBody("{\"message\":\"The history or contributor list is too large to list contributors\"}")));
        stubRepo("new", "empty", "new/empty");
        WIRE_MOCK.stubFor(get(urlPathEqualTo("/repos/new/empty/contributors"))
                .willReturn(aResponse().withStatus(204)));
        stubRepo("solo", "lib", "solo/lib");
        WIRE_MOCK.stubFor(get(urlPathEqualTo("/repos/solo/lib/contributors"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                        .withBody("[{\"login\":\"me\"}]")));

        assertThat(previewService.preview(new RepoRef("big", "monorepo")).contributors()).isNull();
        assertThat(previewService.preview(new RepoRef("new", "empty")).contributors()).isZero();
        assertThat(previewService.preview(new RepoRef("solo", "lib")).contributors()).isEqualTo(1);
    }

    @Test
    void renamedRepositoryReturnsCanonicalName() {
        stubRepo("old-owner", "old-name", "new-owner/new-name");
        WIRE_MOCK.stubFor(get(urlPathEqualTo("/repos/new-owner/new-name/contributors"))
                .willReturn(aResponse().withStatus(204)));

        assertThat(previewService.preview(new RepoRef("old-owner", "old-name")).repo().fullName())
                .isEqualTo("new-owner/new-name");
    }

    @Test
    void deletedRepositoryIsGoneAndCached() {
        WIRE_MOCK.stubFor(get(urlEqualTo("/repos/gone/repo"))
                .willReturn(aResponse().withStatus(404).withHeader("Content-Type", "application/json")
                        .withBody("{\"message\":\"Not Found\"}")));

        assertThatThrownBy(() -> previewService.preview(new RepoRef("gone", "repo")))
                .isInstanceOf(RepoGoneException.class);
        assertThatThrownBy(() -> previewService.preview(new RepoRef("gone", "repo")))
                .isInstanceOf(RepoGoneException.class);
        WIRE_MOCK.verify(1, getRequestedFor(urlEqualTo("/repos/gone/repo")));
    }

    @Test
    void rateLimitPausesRequestsUntilReset() {
        // отдельный экземпляр сервиса: пауза не должна влиять на другие тесты контекста
        Clock clock = Clock.fixed(Instant.parse("2026-11-11T12:00:00Z"), ZoneOffset.UTC);
        GithubPreviewService isolated = new GithubPreviewService(client,
                new GithubProperties(false, null, null, null, null, null, null, null),
                GithubPreviewProperties.defaults(), clock);
        WIRE_MOCK.stubFor(get(urlEqualTo("/repos/limited/one"))
                .willReturn(aResponse().withStatus(403)
                        .withHeader("Content-Type", "application/json")
                        .withHeader("X-RateLimit-Remaining", "0")
                        .withHeader("X-RateLimit-Reset", String.valueOf(Instant.parse("2026-11-11T12:30:00Z").getEpochSecond()))
                        .withBody("{\"message\":\"API rate limit exceeded\"}")));

        assertThatThrownBy(() -> isolated.preview(new RepoRef("limited", "one")))
                .isInstanceOfSatisfying(GithubUnavailableException.class, e -> {
                    assertThat(e.code()).isEqualTo("GITHUB_RATE_LIMITED");
                    assertThat(e.getMessage()).contains("2026-11-11T12:30:00Z").contains("GITHUB_TOKEN");
                });
        assertThatThrownBy(() -> isolated.preview(new RepoRef("limited", "two")))
                .isInstanceOfSatisfying(GithubUnavailableException.class,
                        e -> assertThat(e.code()).isEqualTo("GITHUB_RATE_LIMITED"));
        WIRE_MOCK.verify(0, getRequestedFor(urlEqualTo("/repos/limited/two")));
    }

    @Test
    void disabledPreviewIsReported() {
        GithubPreviewService disabled = new GithubPreviewService(client, GithubProperties.defaults(),
                new GithubPreviewProperties(false, null), Clock.systemUTC());

        assertThatThrownBy(() -> disabled.preview(new RepoRef("any", "repo")))
                .isInstanceOfSatisfying(GithubUnavailableException.class,
                        e -> assertThat(e.code()).isEqualTo("GITHUB_PREVIEW_DISABLED"));
    }

    @Test
    void parsesLastPageFromLinkHeader() {
        assertThat(GithubPreviewService.lastPage(List.of(
                "<https://api.github.com/x?per_page=1&page=2>; rel=\"next\", <https://api.github.com/x?per_page=1&page=9>; rel=\"last\"")))
                .isEqualTo(9);
        assertThat(GithubPreviewService.lastPage(List.of("<https://api.github.com/x?page=1>; rel=\"prev\""))).isNull();
        assertThat(GithubPreviewService.lastPage(null)).isNull();
    }

    @Test
    void endpointReturnsPartialPreview() throws Exception {
        stubRepo("web", "app", "web/app");
        WIRE_MOCK.stubFor(get(urlPathEqualTo("/repos/web/app/contributors"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                        .withHeader("Link", "<https://api.github.com/x?per_page=1&anon=1&page=3>; rel=\"last\"")
                        .withBody("[{}]")));

        mockMvc.perform(get("/api/v1/preview/web/app"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.source").value("github"))
                .andExpect(jsonPath("$.partial").value(true))
                .andExpect(jsonPath("$.note").value(PreviewControllerNote.NOTE))
                .andExpect(jsonPath("$.repo.full_name").value("web/app"))
                .andExpect(jsonPath("$.stars").value(2650))
                .andExpect(jsonPath("$.contributors").value(3))
                .andExpect(jsonPath("$.html_url").value("https://github.com/web/app"))
                .andExpect(jsonPath("$.score").doesNotExist());

        WIRE_MOCK.stubFor(get(urlEqualTo("/repos/web/missing"))
                .willReturn(aResponse().withStatus(404)));
        mockMvc.perform(get("/api/v1/preview/web/missing"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("REPO_NOT_FOUND_ON_GITHUB"));

        mockMvc.perform(get("/api/v1/preview/bad name/app"))
                .andExpect(status().isBadRequest());
    }

    private static void stubRepo(String owner, String name, String fullName) {
        WIRE_MOCK.stubFor(get(urlEqualTo("/repos/" + owner + "/" + name))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"full_name":"%s","description":"A tool","html_url":"https://github.com/%s",
                                 "stargazers_count":2650,"forks_count":240,"open_issues_count":12,
                                 "subscribers_count":80,"archived":false,"fork":false,
                                 "created_at":"2020-01-02T03:04:05Z","pushed_at":"2026-11-10T08:00:00Z"}
                                """.formatted(fullName, fullName))));
    }

    /** Текст пометки из контроллера (он package-private в пакете api). */
    private static final class PreviewControllerNote {
        static final String NOTE = "Нет данных GH Archive: базовая статистика GitHub, без оценки.";
    }
}
