package ru.itmo.ipkn.starguard.core.github;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

class GithubAccountPoolClientTest {
    @Test
    void readsPublicStarsAndCountsRestrictedActivityWithoutExposingPrivateRepos() {
        var profile = new GithubAccountPoolClient(body -> response(user())).profile("U1").orElseThrow();
        assertThat(profile.totalStars()).isEqualTo(5000);
        assertThat(profile.contributions()).isEqualTo(5);
        assertThat(profile.stars()).extracting(GithubAccountPoolClient.Star::repoId).containsExactly("R1");
    }

    @Test
    void partialErrorsAndMissingFieldsCannotBecomeCleanProfiles() {
        var partial = new HashMap<>(response(user()));
        partial.put("errors", List.of(Map.of("type", "FORBIDDEN", "path", List.of("node", "starredRepositories"))));
        assertThatThrownBy(() -> new GithubAccountPoolClient(body -> partial).profile("U1"))
                .isInstanceOf(GithubUnavailableException.class);
        var incomplete = new HashMap<>(user());
        incomplete.remove("contributionsCollection");
        assertThatThrownBy(() -> new GithubAccountPoolClient(body -> response(incomplete)).profile("U1"))
                .isInstanceOf(GithubUnavailableException.class);
        incomplete.put("contributionsCollection", user().get("contributionsCollection"));
        incomplete.put("starredRepositories", Map.of("totalCount", 5000));
        assertThatThrownBy(() -> new GithubAccountPoolClient(body -> response(incomplete)).profile("U1"))
                .isInstanceOf(GithubUnavailableException.class);
    }

    @Test
    void explicitMissingNodeIsUnavailableAccountNotProofOfAbuse() {
        var missing = new HashMap<>(response(null));
        missing.put("errors", List.of(Map.of("type", "NOT_FOUND", "path", List.of("node"))));
        assertThat(new GithubAccountPoolClient(body -> missing).profile("U1")).isEmpty();
    }

    @Test
    void respectsRateReserveUntilReset() {
        var client = new GithubAccountPoolClient(body -> response(user()));
        client.profile("U1");
        assertThat(client.hasBudget(1000, java.time.Instant.parse("2026-11-12T12:00:00Z"))).isFalse();
        assertThat(client.hasBudget(1000, java.time.Instant.parse("2026-11-12T13:00:00Z"))).isTrue();
    }

    @Test
    void paginatesForkOwnersAndIgnoresOrganizations() {
        var node = Map.of("forks", Map.of("nodes", List.of(
                Map.of("owner", Map.of("id", "U1")), Map.of("owner", Map.of()),
                Map.of("owner", Map.of("id", "U1"))),
                "pageInfo", Map.of("hasNextPage", true, "endCursor", "cursor2")));
        var client = new GithubAccountPoolClient(body -> {
            assertThat(((Map<?, ?>) body.get("variables")).get("after")).isEqualTo("cursor1");
            return response(node);
        });
        var page = client.forks("R1", "cursor1", 20);
        assertThat(page.ids()).containsExactly("U1");
        assertThat(page.nextCursor()).isEqualTo("cursor2");
    }

    private static Map<String, Object> response(Map<String, ?> node) {
        var data = new HashMap<String, Object>();
        data.put("node", node);
        data.put("rateLimit", Map.of("remaining", 900, "resetAt", "2026-11-12T13:00:00Z"));
        return Map.of("data", data);
    }

    private static Map<String, Object> user() {
        return Map.of("id", "U1", "repositories", Map.of("totalCount", 0),
                "followers", Map.of("totalCount", 0),
                "contributionsCollection", Map.of("totalCommitContributions", 1,
                        "totalIssueContributions", 0, "totalPullRequestContributions", 0,
                        "totalPullRequestReviewContributions", 0, "restrictedContributionsCount", 4),
                "starredRepositories", Map.of("totalCount", 5000, "edges", List.of(
                        edge("R1", false), edge("private", true))));
    }

    private static Map<String, Object> edge(String id, boolean hidden) {
        return Map.of("starredAt", "2026-11-12T11:00:00Z",
                "node", Map.of("id", id, "nameWithOwner", "owner/" + id, "isPrivate", hidden));
    }
}
