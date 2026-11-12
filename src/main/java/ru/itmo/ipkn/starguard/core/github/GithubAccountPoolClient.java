package ru.itmo.ipkn.starguard.core.github;

import org.springframework.stereotype.Component;
import java.time.Instant;
import java.util.*;

/** Only public data; no repository stargazers endpoint. */
@Component
public class GithubAccountPoolClient {
    private final GithubGraphqlFeignClient graphql;
    private int remaining = Integer.MAX_VALUE;
    private Instant resetAt = Instant.EPOCH;
    public GithubAccountPoolClient(GithubGraphqlFeignClient graphql) { this.graphql = graphql; }

    public boolean hasBudget(int reserve, Instant now) {
        return remaining > reserve || !now.isBefore(resetAt);
    }

    public ForkPage forks(String repoId, String after, int count) {
        Map<String, Object> vars = new HashMap<>();
        vars.put("id", repoId); vars.put("after", after); vars.put("count", count);
        var data = call("""
            query($id: ID!, $after: String, $count: Int!) {
              node(id: $id) { ... on Repository {
                forks(first: $count, after: $after, orderBy: {field: CREATED_AT, direction: DESC}) {
                  nodes { owner { ... on User { id } } }
                  pageInfo { hasNextPage endCursor }
                }
              } }
              rateLimit { remaining resetAt }
            }
            """, vars);
        var forks = Graphql.map(data, "node", "forks");
        if (data.get("node") == null) return new ForkPage(List.of(), null);
        if (forks == null || !(forks.get("nodes") instanceof List<?>)) throw unavailable();
        var ids = new LinkedHashSet<String>();
        for (var raw : Graphql.list(forks, "nodes")) {
            String id = Graphql.string(Graphql.map(raw, "owner"), "id");
            if (id != null) ids.add(id);
        }
        var page = Graphql.map(forks, "pageInfo");
        if (page == null || !(page.get("hasNextPage") instanceof Boolean)) throw unavailable();
        return new ForkPage(List.copyOf(ids), Graphql.bool(page, "hasNextPage")
                ? Objects.requireNonNull(Graphql.string(page, "endCursor")) : null);
    }

    public Optional<Profile> profile(String id) {
        var data = call("""
            query($id: ID!) {
              node(id: $id) { ... on User {
                id
                repositories(privacy: PUBLIC, ownerAffiliations: OWNER, isFork: false) { totalCount }
                followers { totalCount }
                contributionsCollection { totalCommitContributions totalIssueContributions
                  totalPullRequestContributions totalPullRequestReviewContributions restrictedContributionsCount }
                starredRepositories(first: 100, orderBy: {field: STARRED_AT, direction: DESC}) {
                  totalCount edges { starredAt node { id nameWithOwner isPrivate } }
                }
              } }
              rateLimit { remaining resetAt }
            }
            """, Map.of("id", id));
        var node = Graphql.map(data, "node");
        if (node == null) return Optional.empty();
        if (!id.equals(Graphql.string(node, "id"))) throw unavailable();
        var contributions = Graphql.map(node, "contributionsCollection");
        long activity = 0;
        for (String field : List.of("totalCommitContributions", "totalIssueContributions",
                "totalPullRequestContributions", "totalPullRequestReviewContributions", "restrictedContributionsCount"))
            activity += requiredCount(contributions, field);
        var starred = Graphql.map(node, "starredRepositories");
        if (starred == null || !(starred.get("edges") instanceof List<?>)) throw unavailable();
        var stars = new ArrayList<Star>();
        var seen = new HashSet<String>();
        for (var raw : Graphql.list(starred, "edges")) {
            var edge = Graphql.map(raw);
            var repo = Graphql.map(edge, "node");
            if (repo == null || !(repo.get("isPrivate") instanceof Boolean)) throw unavailable();
            if (Graphql.bool(repo, "isPrivate")) continue;
            String repoId = Graphql.string(repo, "id"), name = Graphql.string(repo, "nameWithOwner");
            Instant at = Graphql.instant(edge, "starredAt");
            if (repoId == null || name == null || at == null) throw unavailable();
            if (seen.add(repoId)) stars.add(new Star(repoId, name, at));
        }
        return Optional.of(new Profile(id, requiredCount(Graphql.map(node, "repositories"), "totalCount"),
                requiredCount(Graphql.map(node, "followers"), "totalCount"), (int) Math.min(Integer.MAX_VALUE, activity),
                requiredCount(starred, "totalCount"), List.copyOf(stars)));
    }

    private Map<String, Object> call(String query, Map<String, Object> variables) {
        var response = graphql.query(Map.of("query", query, "variables", variables));
        if (response == null) throw unavailable();
        var data = Graphql.map(response, "data");
        // Only an explicit missing node is absence. Permission/timeout/partial field errors are failures.
        for (Object raw : Graphql.list(response, "errors")) {
            var error = Graphql.map(raw);
            if (!"NOT_FOUND".equals(Graphql.string(error, "type"))
                    || !List.of("node").equals(Graphql.list(error, "path"))
                    || data == null || data.get("node") != null) throw unavailable();
        }
        if (data == null || !data.containsKey("node")) throw unavailable();
        var rate = Graphql.map(data, "rateLimit");
        remaining = requiredCount(rate, "remaining");
        resetAt = Graphql.instant(rate, "resetAt");
        if (resetAt == null || data == null) throw unavailable();
        return data;
    }
    private static int requiredCount(Map<String, Object> node, String field) {
        long n = Graphql.number(node, field, -1);
        if (n < 0 || n > Integer.MAX_VALUE) throw unavailable();
        return (int) n;
    }
    private static GithubUnavailableException unavailable() {
        return new GithubUnavailableException("GITHUB_UNAVAILABLE", "Incomplete account pool response");
    }
    public record Star(String repoId, String fullName, Instant at) {}
    public record Profile(String id, int repos, int followers, int contributions, int totalStars, List<Star> stars) {}
    public record ForkPage(List<String> ids, String nextCursor) {}
}
