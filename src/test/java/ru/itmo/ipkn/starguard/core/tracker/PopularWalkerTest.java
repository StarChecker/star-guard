package ru.itmo.ipkn.starguard.core.tracker;

import org.junit.jupiter.api.Test;
import ru.itmo.ipkn.starguard.core.github.GithubGraphqlFeignClient;
import ru.itmo.ipkn.starguard.core.github.GithubTrackerClient;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PopularWalkerTest {

    @Test
    void walksStarWindowsDownwardsAndSkipsBoundaryDuplicates() {
        FakeSearch search = new FakeSearch()
                .window("stars:>=1 sort:stars", repos(1, 1000, 10_000), 5000)
                .window("stars:1..9001 sort:stars", repos(1000, 1500, 9001), 5000);

        PopularWalker.Result result = new PopularWalker(new GithubTrackerClient(search), Duration.ZERO, 500).walk(1200);

        assertThat(result.ranked()).hasSize(1200);
        assertThat(result.ranked().getFirst().nodeId()).isEqualTo("R_1");
        assertThat(result.ranked().get(999).nodeId()).isEqualTo("R_1000");
        assertThat(result.ranked().get(1000).nodeId()).isEqualTo("R_1001");
        assertThat(result.complete()).isTrue();
        assertThat(search.queries).containsExactly("stars:>=1 sort:stars", "stars:1..9001 sort:stars");
    }

    @Test
    void stopsWhenRateLimitReserveIsReached() {
        FakeSearch search = new FakeSearch().window("stars:>=1 sort:stars", repos(1, 1000, 10_000), 300);

        PopularWalker.Result result = new PopularWalker(new GithubTrackerClient(search), Duration.ZERO, 500).walk(5000);

        assertThat(result.ranked()).hasSize(100);
        assertThat(result.complete()).isFalse();
        assertThat(result.rateRemaining()).isEqualTo(300);
    }

    @Test
    void stopsWhenSearchRunsOutOfRepositories() {
        FakeSearch search = new FakeSearch().window("stars:>=1 sort:stars", repos(1, 50, 100), 5000);

        PopularWalker.Result result = new PopularWalker(new GithubTrackerClient(search), Duration.ZERO, 500).walk(1000);

        assertThat(result.ranked()).hasSize(50);
        assertThat(result.complete()).isFalse();
    }

    private static List<Map<String, Object>> repos(int from, int to, int topStars) {
        List<Map<String, Object>> repos = new ArrayList<>();
        for (int i = from; i <= to; i++) {
            repos.add(Map.of("id", "R_" + i, "nameWithOwner", "owner/repo" + i,
                    "createdAt", "2020-01-01T00:00:00Z", "stargazerCount", topStars - (i - from), "forkCount", 1));
        }
        return repos;
    }

    static final class FakeSearch implements GithubGraphqlFeignClient {

        private final Map<String, List<Map<String, Object>>> windows = new HashMap<>();
        private final Map<String, Integer> remaining = new HashMap<>();
        final List<String> queries = new ArrayList<>();

        FakeSearch window(String query, List<Map<String, Object>> repos, int rateRemaining) {
            windows.put(query, repos);
            remaining.put(query, rateRemaining);
            return this;
        }

        @Override
        @SuppressWarnings("unchecked")
        public Map<String, Object> query(Map<String, Object> body) {
            Map<String, Object> variables = (Map<String, Object>) body.get("variables");
            String query = variables.get("q").toString();
            Object after = variables.get("after");
            if (after == null) {
                queries.add(query);
            }
            List<Map<String, Object>> repos = windows.getOrDefault(query, List.of());
            int offset = after == null ? 0 : Integer.parseInt(after.toString());
            int end = Math.min(repos.size(), offset + 100);
            Map<String, Object> pageInfo = new HashMap<>();
            pageInfo.put("hasNextPage", end < repos.size());
            pageInfo.put("endCursor", String.valueOf(end));
            return Map.of("data", Map.of(
                    "search", Map.of("repositoryCount", repos.size(), "pageInfo", pageInfo,
                            "nodes", repos.subList(offset, end)),
                    "rateLimit", Map.of("remaining", remaining.getOrDefault(query, 5000),
                            "resetAt", "2026-11-12T12:00:00Z")));
        }
    }
}
