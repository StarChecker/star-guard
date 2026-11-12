package ru.itmo.ipkn.starguard.core.github;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import ru.itmo.ipkn.starguard.core.archive.ArchiveProgress;
import ru.itmo.ipkn.starguard.core.archive.InvalidWindowException;
import ru.itmo.ipkn.starguard.core.config.ArchiveProperties;
import ru.itmo.ipkn.starguard.core.config.GithubHistoryProperties;
import ru.itmo.ipkn.starguard.core.config.GithubProperties;
import ru.itmo.ipkn.starguard.core.domain.DataSource;
import ru.itmo.ipkn.starguard.core.domain.GithubSignals;
import ru.itmo.ipkn.starguard.core.domain.RepoRef;
import ru.itmo.ipkn.starguard.core.service.IntradaySource;
import ru.itmo.ipkn.starguard.core.service.RepoData;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GithubHistoryServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-12T10:30:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final RepoRef REPO = new RepoRef("acme", "tool");
    private static final long WEEK_NOV_01 = LocalDate.parse("2026-11-01").atStartOfDay(ZoneOffset.UTC).toEpochSecond();
    private static final long WEEK_NOV_08 = LocalDate.parse("2026-11-08").atStartOfDay(ZoneOffset.UTC).toEpochSecond();

    @Test
    void buildsDailyHistoryAndWindowActivityForNewRepository() {
        FakeRest rest = new FakeRest().page(1, false,
                week(WEEK_NOV_08, 1617, 2859, 1161, 202, 70, 0, 0),
                week(WEEK_NOV_01, 0, 0, 273, 680, 328, 536, 682));
        FakeGraphql graphql = new FakeGraphql().repository("2026-11-03T14:04:27Z", 8408, 4394,
                forks(3102, false, List.of()), 1, List.of("farm-owner"), 38, 26);

        RepoData data = service(graphql, rest, null, GithubHistoryProperties.defaults())
                .load(REPO, ArchiveProgress.NONE);

        GithubSignals github = data.aggregate().snapshot().github();
        assertThat(github.firstDay()).isEqualTo(LocalDate.parse("2026-11-03"));
        assertThat(github.dailyStars()).containsExactly(273, 680, 328, 536, 682, 1617, 2859, 1161, 202, 70);
        assertThat(github.totalStars()).isEqualTo(8408);
        assertThat(github.repoCreatedAt()).isEqualTo(Instant.parse("2026-11-03T14:04:27Z"));
        assertThat(github.activity().stars()).isEqualTo(8408);
        assertThat(github.activity().forks()).isEqualTo(3102);
        assertThat(github.activity().issues()).isEqualTo(38);
        assertThat(github.activity().pullRequests()).isEqualTo(26);
        assertThat(github.activity().commits()).isEqualTo(1);
        assertThat(github.activity().contributors()).isEqualTo(1);
        assertThat(github.intraday()).isNull();
        assertThat(data.window().from()).isEqualTo(Instant.parse("2026-10-14T00:00:00Z"));
        assertThat(data.window().to()).isEqualTo(Instant.parse("2026-11-12T11:00:00Z"));
        assertThat(data.aggregate().stats().counts().stars()).isEqualTo(8408);
        assertThat(data.aggregate().stats().counts().pushes()).isEqualTo(1);
        assertThat(data.source().type()).isEqualTo(DataSource.Type.GITHUB_API);
        assertThat(data.source().starsTotal()).isEqualTo(8408);
        assertThat(rest.pages).containsExactly(1);
        assertThat(graphql.forkPages).isZero();
    }

    @Test
    void countsWindowForksPageByPageForOlderRepository() {
        List<String> firstPage = times(100, "2026-11-10T00:00:00Z");
        List<String> secondPage = new ArrayList<>(times(30, "2026-10-20T00:00:00Z"));
        secondPage.add("2026-10-01T00:00:00Z");
        FakeGraphql graphql = new FakeGraphql()
                .repository("2020-01-01T00:00:00Z", 5000, 900, forks(900, true, firstPage), 40,
                        List.of("a", "b", "a"), 12, 9)
                .forkPage(forks(0, true, secondPage));

        RepoData data = service(graphql, new FakeRest().page(1, false), null, GithubHistoryProperties.defaults())
                .load(REPO, ArchiveProgress.NONE);

        GithubSignals.WindowActivity activity = data.aggregate().snapshot().github().activity();
        assertThat(activity.forks()).isEqualTo(130);
        assertThat(activity.forksTruncated()).isFalse();
        assertThat(activity.contributors()).isEqualTo(2);
        assertThat(graphql.forkPages).isEqualTo(1);
    }

    @Test
    void marksForksAsTruncatedAfterPageLimit() {
        FakeGraphql graphql = new FakeGraphql().repository("2020-01-01T00:00:00Z", 5000, 900,
                forks(900, true, times(100, "2026-11-10T00:00:00Z")), 0, List.of(), 0, 0);

        RepoData data = service(graphql, new FakeRest().page(1, false), null,
                new GithubHistoryProperties(30, 180, 1, null, null, null))
                .load(REPO, ArchiveProgress.NONE);

        assertThat(data.aggregate().snapshot().github().activity().forks()).isEqualTo(100);
        assertThat(data.aggregate().snapshot().github().activity().forksTruncated()).isTrue();
    }

    @Test
    void oldRepositoryHistoryStartsAtHistoryDaysAndFollowsPages() {
        FakeRest rest = new FakeRest()
                .page(1, true, week(WEEK_NOV_08, 5, 5, 5, 5, 5, 0, 0))
                .page(2, false, week(LocalDate.parse("2025-10-19").atStartOfDay(ZoneOffset.UTC).toEpochSecond(),
                        9, 9, 9, 9, 9, 9, 9));
        FakeGraphql graphql = new FakeGraphql().repository("2015-01-01T00:00:00Z", 100_000, 10_000,
                forks(10_000, false, List.of()), 0, List.of(), 0, 0);

        RepoData data = service(graphql, rest, null, new GithubHistoryProperties(30, 365, null, null, null, null))
                .load(REPO, ArchiveProgress.NONE);

        GithubSignals github = data.aggregate().snapshot().github();
        assertThat(github.firstDay()).isEqualTo(LocalDate.parse("2025-11-13"));
        assertThat(github.days()).isEqualTo(365);
        assertThat(github.dailyStars()[github.days() - 1]).isEqualTo(5);
        assertThat(rest.pages).containsExactly(1, 2);
    }

    @Test
    void deletedRepositoryIsReportedAsGone() {
        FakeGraphql graphql = new FakeGraphql();
        graphql.gone = true;

        assertThatThrownBy(() -> service(graphql, new FakeRest(), null, GithubHistoryProperties.defaults())
                .load(REPO, ArchiveProgress.NONE))
                .isInstanceOf(RepoGoneException.class);
    }

    @Test
    void tokenIsRequired() {
        GithubHistoryService service = new GithubHistoryService(new FakeGraphql(), new FakeRest(),
                GithubProperties.defaults(), GithubHistoryProperties.defaults(), archive(), provider(null), CLOCK);

        assertThat(service.available()).isFalse();
        assertThatThrownBy(() -> service.load(REPO, ArchiveProgress.NONE))
                .isInstanceOf(GithubUnavailableException.class)
                .hasMessageContaining("token");
    }

    @Test
    void rejectsWindowLongerThanConfigured() {
        GithubHistoryService service = service(new FakeGraphql(), new FakeRest(), null, GithubHistoryProperties.defaults());

        assertThatThrownBy(() -> service.load(REPO, 31, ArchiveProgress.NONE))
                .isInstanceOf(InvalidWindowException.class);
    }

    @Test
    void watchesRepositoryAndAttachesTrackerData() {
        FakeTracker tracker = new FakeTracker();
        GithubHistoryService service = service(newRepoGraphql(), newRepoRest(), tracker,
                GithubHistoryProperties.defaults());

        RepoData data = service.load(REPO, ArchiveProgress.NONE);

        assertThat(tracker.watched).containsExactly("R_1");
        assertThat(data.aggregate().snapshot().github().intraday()).isSameAs(tracker.intraday);
        assertThat(data.aggregate().stats().peakStars10m()).isEqualTo(7);
        assertThat(data.aggregate().snapshot().data().globalHourProfile()).isTrue();
    }

    @Test
    void trackerFailureDoesNotBreakTheReport() {
        FakeTracker tracker = new FakeTracker();
        tracker.broken = true;

        RepoData data = service(newRepoGraphql(), newRepoRest(), tracker, GithubHistoryProperties.defaults())
                .load(REPO, ArchiveProgress.NONE);

        assertThat(data.aggregate().snapshot().github().intraday()).isNull();
    }

    @Test
    void cachesGithubDataBetweenReports() {
        FakeGraphql graphql = newRepoGraphql();
        FakeRest rest = newRepoRest();
        GithubHistoryService service = service(graphql, rest, null, GithubHistoryProperties.defaults());

        service.load(REPO, ArchiveProgress.NONE);
        service.load(REPO, ArchiveProgress.NONE);

        assertThat(graphql.repositoryCalls).isEqualTo(1);
        assertThat(rest.pages).hasSize(1);
    }

    private static FakeGraphql newRepoGraphql() {
        return new FakeGraphql().repository("2026-11-03T14:04:27Z", 8408, 4394,
                forks(3102, false, List.of()), 1, List.of(), 38, 26);
    }

    private static FakeRest newRepoRest() {
        return new FakeRest().page(1, false, week(WEEK_NOV_08, 1617, 2859, 1161, 202, 70, 0, 0));
    }

    private static GithubHistoryService service(
            FakeGraphql graphql, FakeRest rest, IntradaySource tracker, GithubHistoryProperties properties) {
        return new GithubHistoryService(graphql, rest, new GithubProperties(true, null, "token", null, null, null,
                null, null), properties, archive(), provider(tracker), CLOCK);
    }

    private static ObjectProvider<IntradaySource> provider(IntradaySource tracker) {
        DefaultListableBeanFactory factory = new DefaultListableBeanFactory();
        if (tracker != null) {
            factory.registerSingleton("tracker", tracker);
        }
        return factory.getBeanProvider(IntradaySource.class);
    }

    private static ArchiveProperties archive() {
        return archive(ArchiveProperties.Mode.HTTP);
    }

    static ArchiveProperties archive(ArchiveProperties.Mode mode) {
        return new ArchiveProperties("https://example.test", mode, 6, 24, 2, 2, 0, false,
                Duration.ofMinutes(5), "classpath:none", null);
    }

    private static StarHistoryWeek week(long week, Integer... days) {
        int total = 0;
        for (int day : days) {
            total += day;
        }
        return new StarHistoryWeek(week, total, List.of(days));
    }

    private static List<String> times(int count, String at) {
        List<String> times = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            times.add(at);
        }
        return times;
    }

    private static Map<String, Object> forks(int total, boolean hasNext, List<String> createdAt) {
        return Map.of(
                "totalCount", total,
                "pageInfo", hasNext ? Map.of("hasNextPage", true, "endCursor", "c1") : Map.of("hasNextPage", false),
                "nodes", createdAt.stream().map(at -> Map.of("createdAt", at)).toList());
    }

    static final class FakeRest implements GithubReposFeignClient {

        private final Map<Integer, ResponseEntity<List<StarHistoryWeek>>> pagesByNumber = new HashMap<>();
        final List<Integer> pages = new ArrayList<>();

        FakeRest page(int number, boolean hasNext, StarHistoryWeek... weeks) {
            HttpHeaders headers = new HttpHeaders();
            if (hasNext) {
                headers.add("Link", "<https://api.github.com/repositories/1/stargazers/history?page="
                        + (number + 1) + ">; rel=\"next\"");
            }
            pagesByNumber.put(number, ResponseEntity.ok().headers(headers).body(List.of(weeks)));
            return this;
        }

        @Override
        public GithubRepoInfo repo(String owner, String name) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ResponseEntity<List<Map<String, Object>>> contributors(String owner, String name, int perPage, String anon) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ResponseEntity<List<StarHistoryWeek>> starHistory(String owner, String name, int page) {
            pages.add(page);
            return pagesByNumber.getOrDefault(page, ResponseEntity.ok(List.of()));
        }
    }

    static final class FakeGraphql implements GithubGraphqlFeignClient {

        private Map<String, Object> repository;
        private final List<Map<String, Object>> forkPagesQueue = new ArrayList<>();
        boolean gone;
        int forkPages;
        int repositoryCalls;

        FakeGraphql repository(String createdAt, long stars, long forkCount, Map<String, Object> forks,
                               int commits, List<String> authors, int issueCount, int pullCount) {
            Map<String, Object> repo = new HashMap<>();
            repo.put("id", "R_1");
            repo.put("createdAt", createdAt);
            repo.put("stargazerCount", stars);
            repo.put("forkCount", forkCount);
            repo.put("forks", forks);
            repo.put("issues", Map.of("totalCount", issueCount, "pageInfo", Map.of("hasNextPage", false),
                    "nodes", List.of()));
            repo.put("pullRequests", Map.of("totalCount", pullCount, "pageInfo", Map.of("hasNextPage", false),
                    "nodes", List.of()));
            repo.put("defaultBranchRef", Map.of("target", Map.of("history", Map.of(
                    "totalCount", commits,
                    "nodes", authors.stream().map(login -> Map.of("author", Map.of("user", Map.of("login", login))))
                            .toList()))));
            this.repository = repo;
            return this;
        }

        FakeGraphql forkPage(Map<String, Object> page) {
            forkPagesQueue.add(page);
            return this;
        }

        @Override
        public Map<String, Object> query(Map<String, Object> body) {
            if (gone) {
                Map<String, Object> data = new HashMap<>();
                data.put("repository", null);
                return Map.of("data", data, "errors", List.of(Map.of("type", "NOT_FOUND")));
            }
            String query = body.get("query").toString();
            if (query.contains("after: $after")) {
                Map<String, Object> page = forkPagesQueue.get(forkPages++);
                return Map.of("data", Map.of("repository", Map.of("forks", page)));
            }
            repositoryCalls++;
            return Map.of("data", Map.of("repository", repository));
        }
    }

    static final class FakeTracker implements IntradaySource {

        final List<String> watched = new ArrayList<>();
        boolean broken;
        final GithubSignals.Intraday intraday = new GithubSignals.Intraday(
                Instant.parse("2026-11-11T10:25:00Z"), 5, new int[]{100, 101, 105, 108, -1, 112},
                Instant.parse("2026-11-05T10:00:00Z"), new int[]{90, 100}, new double[24],
                Instant.parse("2026-11-01T00:00:00Z"));

        @Override
        public void watch(String nodeId, RepoRef repo, Instant createdAt, long stars, long forks) {
            if (broken) {
                throw new IllegalStateException("database is down");
            }
            watched.add(nodeId);
        }

        @Override
        public Optional<GithubSignals.Intraday> intraday(String nodeId) {
            return Optional.of(intraday);
        }
    }
}
