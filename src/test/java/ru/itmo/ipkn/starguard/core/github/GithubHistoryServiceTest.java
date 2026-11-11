package ru.itmo.ipkn.starguard.core.github;

import org.junit.jupiter.api.Test;
import ru.itmo.ipkn.starguard.core.archive.ArchiveProgress;
import ru.itmo.ipkn.starguard.core.config.ArchiveProperties;
import ru.itmo.ipkn.starguard.core.config.GithubHistoryProperties;
import ru.itmo.ipkn.starguard.core.config.GithubProperties;
import ru.itmo.ipkn.starguard.core.domain.DataSource;
import ru.itmo.ipkn.starguard.core.domain.EventType;
import ru.itmo.ipkn.starguard.core.domain.GhEvent;
import ru.itmo.ipkn.starguard.core.domain.RepoRef;
import ru.itmo.ipkn.starguard.core.service.RepoData;
import ru.itmo.ipkn.starguard.core.stats.StatsAggregator;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GithubHistoryServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-11T12:30:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final RepoRef REPO = new RepoRef("acme", "tool");

    @Test
    void buildsReportDataFromStarsAndActivity() {
        // 70 свежих аккаунтов за час + 30 старых звёзд, растянутых на 20 дней
        List<Map<String, Object>> stars = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            stars.add(star(NOW.minus(Duration.ofDays(20 - i / 2)), 1000 + i, "2015-01-01T00:00:00Z"));
        }
        for (int i = 0; i < 70; i++) {
            stars.add(star(Instant.parse("2026-11-10T10:00:00Z").plusSeconds(i * 40L), 5000 + i, "2026-11-01T00:00:00Z"));
        }
        FakeGraphql graphql = new FakeGraphql()
                .starsPage(null, stars, false, null, 100)
                .activity(activity(2, 1, 3));
        List<int[]> progress = new ArrayList<>();

        RepoData data = service(graphql, 3000).load(REPO, (done, total) -> progress.add(new int[]{done, total}));

        assertThat(data.source().type()).isEqualTo(DataSource.Type.GITHUB_API);
        assertThat(data.source().starsSampled()).isEqualTo(100);
        assertThat(data.source().starsTotal()).isEqualTo(100);
        assertThat(data.window().to()).isEqualTo(Instant.parse("2026-11-11T13:00:00Z"));
        assertThat(data.window().from()).isEqualTo(Instant.parse("2026-10-12T12:00:00Z"));
        assertThat(data.stars()).hasSize(100);
        assertThat(data.aggregate().stats().counts().stars()).isEqualTo(100);
        assertThat(data.aggregate().stats().counts().forks()).isEqualTo(2);
        assertThat(data.aggregate().stats().counts().issues()).isEqualTo(1);
        assertThat(data.aggregate().stats().counts().pushes()).isEqualTo(1); // 3 коммита одного автора в один час
        assertThat(data.archive().events())
                .filteredOn(event -> event.type() == EventType.WATCH)
                .allSatisfy(event -> assertThat(event.actorCreatedAt()).isNotNull());
        assertThat(data.aggregate().stats().peakStars10m()).isGreaterThanOrEqualTo(14);
        assertThat(progress.getLast()).containsExactly(2, 2);
    }

    @Test
    void limitsWindowWhenStarsAreTruncated() {
        List<Map<String, Object>> newer = new ArrayList<>();
        List<Map<String, Object>> older = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            newer.add(star(Instant.parse("2026-11-10T00:00:00Z").plusSeconds(i * 60L), 1 + i, null));
            older.add(star(Instant.parse("2026-11-08T00:00:00Z").plusSeconds(i * 60L), 101 + i, null));
        }
        FakeGraphql graphql = new FakeGraphql()
                .starsPage(null, newer, true, "c1", 5000)
                .starsPage("c1", older, true, "c2", 5000)
                .activity(activity(0, 0, 0));

        RepoData data = service(graphql, 200).load(REPO, ArchiveProgress.NONE);

        assertThat(data.window().from()).isEqualTo(Instant.parse("2026-11-08T00:00:00Z"));
        assertThat(data.stars()).hasSize(200);
        assertThat(data.source().starsTotal()).isEqualTo(5000);
        assertThat(graphql.starPages).isEqualTo(2);
    }

    @Test
    void deletedRepositoryIsReportedAsGone() {
        FakeGraphql graphql = new FakeGraphql();
        graphql.gone = true;

        assertThatThrownBy(() -> service(graphql, 3000).load(REPO, ArchiveProgress.NONE))
                .isInstanceOf(RepoGoneException.class);
    }

    @Test
    void tokenIsRequired() {
        GithubHistoryService service = new GithubHistoryService(new FakeGraphql(),
                GithubProperties.defaults(), GithubHistoryProperties.defaults(), archive(ArchiveProperties.Mode.HTTP),
                new StatsAggregator(CLOCK), CLOCK);

        assertThat(service.available()).isFalse();
        assertThatThrownBy(() -> service.load(REPO, ArchiveProgress.NONE))
                .isInstanceOfSatisfying(GithubUnavailableException.class,
                        e -> assertThat(e.code()).isEqualTo("GITHUB_TOKEN_REQUIRED"));
    }

    private static GithubHistoryService service(FakeGraphql graphql, int maxStars) {
        return new GithubHistoryService(graphql,
                new GithubProperties(false, null, "token", null, null, null, null, null),
                new GithubHistoryProperties(30, maxStars, null, null),
                archive(ArchiveProperties.Mode.HTTP),
                new StatsAggregator(CLOCK), CLOCK);
    }

    static ArchiveProperties archive(ArchiveProperties.Mode mode) {
        return new ArchiveProperties("https://example.test", mode, 6, 24, 2, 2, 0, false,
                Duration.ofMinutes(5), "classpath:none", null);
    }

    private static Map<String, Object> star(Instant at, long id, String createdAt) {
        Map<String, Object> node = new HashMap<>();
        node.put("databaseId", id);
        node.put("login", "user" + id);
        node.put("createdAt", createdAt);
        return Map.of("starredAt", at.toString(), "node", node);
    }

    private static Map<String, Object> activity(int forks, int issues, int commits) {
        List<Object> forkNodes = new ArrayList<>();
        for (int i = 0; i < forks; i++) {
            forkNodes.add(Map.of("createdAt", NOW.minus(Duration.ofDays(1)).toString(),
                    "owner", Map.of("login", "f" + i, "databaseId", 9000 + i)));
        }
        List<Object> issueNodes = new ArrayList<>();
        for (int i = 0; i < issues; i++) {
            issueNodes.add(Map.of("createdAt", NOW.minus(Duration.ofDays(2)).toString(),
                    "author", Map.of("login", "i" + i, "databaseId", 9100 + i)));
        }
        List<Object> commitNodes = new ArrayList<>();
        for (int i = 0; i < commits; i++) {
            commitNodes.add(Map.of("committedDate", NOW.minus(Duration.ofHours(3)).plusSeconds(i).toString(),
                    "author", Map.of("user", Map.of("login", "dev", "databaseId", 42))));
        }
        Map<String, Object> noMore = Map.of("hasPreviousPage", false);
        return Map.of(
                "forks", Map.of("pageInfo", noMore, "nodes", forkNodes),
                "issues", Map.of("pageInfo", noMore, "nodes", issueNodes),
                "pullRequests", Map.of("pageInfo", noMore, "nodes", List.of()),
                "defaultBranchRef", Map.of("target", Map.of("history",
                        Map.of("pageInfo", Map.of("hasNextPage", false), "nodes", commitNodes))));
    }

    /** Подменяет GitHub GraphQL: страницы звёзд по курсору и запрос активности. */
    static final class FakeGraphql implements GithubGraphqlFeignClient {

        private final Map<String, Map<String, Object>> starPagesByCursor = new HashMap<>();
        private Map<String, Object> activity = Map.of();
        boolean gone;
        int starPages;

        FakeGraphql starsPage(String before, List<Map<String, Object>> newestLast, boolean hasPrevious,
                              String startCursor, long total) {
            // GraphQL отдаёт страницу от старых к новым: сортируем по starredAt
            List<Map<String, Object>> edges = new ArrayList<>(newestLast);
            edges.sort((a, b) -> a.get("starredAt").toString().compareTo(b.get("starredAt").toString()));
            Map<String, Object> pageInfo = new HashMap<>();
            pageInfo.put("hasPreviousPage", hasPrevious);
            pageInfo.put("startCursor", startCursor);
            starPagesByCursor.put(String.valueOf(before), Map.of(
                    "stargazerCount", total,
                    "stargazers", Map.of("pageInfo", pageInfo, "edges", edges)));
            return this;
        }

        FakeGraphql activity(Map<String, Object> repository) {
            this.activity = repository;
            return this;
        }

        @Override
        @SuppressWarnings("unchecked")
        public Map<String, Object> query(Map<String, Object> body) {
            if (gone) {
                Map<String, Object> data = new HashMap<>();
                data.put("repository", null);
                return Map.of("data", data, "errors", List.of(Map.of("type", "NOT_FOUND")));
            }
            String query = body.get("query").toString();
            Map<String, Object> variables = (Map<String, Object>) body.get("variables");
            if (query.contains("stargazers(")) {
                starPages++;
                return Map.of("data", Map.of("repository",
                        starPagesByCursor.get(String.valueOf(variables.get("before")))));
            }
            return Map.of("data", Map.of("repository", activity));
        }
    }
}
