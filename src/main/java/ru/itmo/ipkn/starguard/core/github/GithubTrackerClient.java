package ru.itmo.ipkn.starguard.core.github;

import feign.FeignException;
import org.springframework.stereotype.Component;
import ru.itmo.ipkn.starguard.core.domain.RepoRef;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Component
public class GithubTrackerClient {

    private static final String NODES_QUERY = """
            query($ids: [ID!]!) {
              nodes(ids: $ids) { ... on Repository { id stargazerCount forkCount } }
              rateLimit { remaining resetAt }
            }
            """;

    private static final String SEARCH_QUERY = """
            query($q: String!, $after: String) {
              search(type: REPOSITORY, query: $q, first: 100, after: $after) {
                repositoryCount
                pageInfo { hasNextPage endCursor }
                nodes { ... on Repository { id nameWithOwner createdAt stargazerCount forkCount } }
              }
              rateLimit { remaining resetAt }
            }
            """;

    private static final String REPOSITORY_QUERY = """
            query($owner: String!, $name: String!) {
              repository(owner: $owner, name: $name) { id nameWithOwner createdAt stargazerCount forkCount }
              rateLimit { remaining resetAt }
            }
            """;

    private final GithubGraphqlFeignClient graphql;

    public GithubTrackerClient(GithubGraphqlFeignClient graphql) {
        this.graphql = graphql;
    }

    public PollResult poll(List<String> nodeIds) {
        Map<String, Object> response = call(NODES_QUERY, Map.of("ids", nodeIds));
        Map<String, Object> data = Graphql.map(response, "data");
        List<Object> nodes = Graphql.list(data, "nodes");
        Map<String, Counts> counts = new HashMap<>();
        Set<String> missing = new HashSet<>(nodeIds);
        for (Object raw : nodes) {
            Map<String, Object> node = Graphql.map(raw);
            String id = Graphql.string(node, "id");
            if (id != null) {
                counts.put(id, new Counts(
                        (int) Graphql.number(node, "stargazerCount", 0),
                        (int) Graphql.number(node, "forkCount", 0)));
                missing.remove(id);
            }
        }
        if (data == null) {
            throw new GithubUnavailableException("GITHUB_UNAVAILABLE", "GitHub returned no data for tracked repositories");
        }
        return new PollResult(counts, missing, rate(data));
    }

    public SearchPage search(String query, String after) {
        Map<String, Object> variables = new HashMap<>();
        variables.put("q", query);
        variables.put("after", after);
        Map<String, Object> data = Graphql.map(call(SEARCH_QUERY, variables), "data");
        Map<String, Object> search = Graphql.map(data, "search");
        if (search == null) {
            throw new GithubUnavailableException("GITHUB_UNAVAILABLE", "GitHub search returned no data");
        }
        List<RepoHit> repos = new ArrayList<>();
        for (Object raw : Graphql.list(search, "nodes")) {
            hit(Graphql.map(raw)).ifPresent(repos::add);
        }
        Map<String, Object> pageInfo = Graphql.map(search, "pageInfo");
        return new SearchPage(
                (int) Graphql.number(search, "repositoryCount", 0),
                Graphql.bool(pageInfo, "hasNextPage"),
                Graphql.string(pageInfo, "endCursor"),
                repos,
                rate(data));
    }

    public Optional<RepoHit> repository(RepoRef repo) {
        Map<String, Object> response = call(REPOSITORY_QUERY, Map.of("owner", repo.owner(), "name", repo.name()));
        return hit(Graphql.map(response, "data", "repository"));
    }

    private Map<String, Object> call(String query, Map<String, Object> variables) {
        try {
            Map<String, Object> response = graphql.query(Map.of("query", query, "variables", variables));
            if (response == null) {
                throw new GithubUnavailableException("GITHUB_UNAVAILABLE", "GitHub returned an empty response");
            }
            return response;
        } catch (FeignException e) {
            if (e.status() == 401) {
                throw new GithubUnavailableException("GITHUB_TOKEN_REQUIRED", "GitHub rejected the token", e);
            }
            if (e.status() == 403 || e.status() == 429) {
                throw new GithubUnavailableException("GITHUB_RATE_LIMITED", "GitHub rate limit: HTTP " + e.status(), e);
            }
            throw new GithubUnavailableException("GITHUB_UNAVAILABLE", "GitHub API request failed: HTTP " + e.status(), e);
        }
    }

    private static Optional<RepoHit> hit(Map<String, Object> node) {
        String id = Graphql.string(node, "id");
        String name = Graphql.string(node, "nameWithOwner");
        if (id == null || name == null) {
            return Optional.empty();
        }
        return Optional.of(new RepoHit(id, name, Graphql.instant(node, "createdAt"),
                (int) Graphql.number(node, "stargazerCount", 0),
                (int) Graphql.number(node, "forkCount", 0)));
    }

    private static RateLimit rate(Map<String, Object> data) {
        Map<String, Object> rate = Graphql.map(data, "rateLimit");
        return new RateLimit((int) Graphql.number(rate, "remaining", Integer.MAX_VALUE),
                Graphql.instant(rate, "resetAt"));
    }

    public record Counts(int stars, int forks) {
    }

    public record RateLimit(int remaining, Instant resetAt) {
    }

    public record PollResult(Map<String, Counts> counts, Set<String> missing, RateLimit rateLimit) {
    }

    public record RepoHit(String nodeId, String fullName, Instant createdAt, int stars, int forks) {
    }

    public record SearchPage(int total, boolean hasNext, String endCursor, List<RepoHit> repos, RateLimit rateLimit) {
    }
}
