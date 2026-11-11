package ru.itmo.ipkn.starguard.core.github;

import org.junit.jupiter.api.Test;
import ru.itmo.ipkn.starguard.core.config.ArchiveProperties;
import ru.itmo.ipkn.starguard.core.config.GithubHistoryProperties;
import ru.itmo.ipkn.starguard.core.config.GithubProperties;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class RepoExistenceServiceTest {

    @Test
    void checksInGraphqlBatchesAndCaches() {
        List<String> queries = new ArrayList<>();
        GithubGraphqlFeignClient graphql = body -> {
            queries.add(body.get("query").toString());
            Map<String, Object> data = new HashMap<>();
            data.put("r0", Map.of("id", "R_1"));
            data.put("r1", null);
            return Map.of("data", data);
        };
        RepoExistenceService service = new RepoExistenceService(graphql,
                new GithubProperties(false, null, "token", null, null, null, null, null),
                GithubHistoryProperties.defaults(),
                GithubHistoryServiceTest.archive(ArchiveProperties.Mode.HTTP),
                name -> { throw new AssertionError("web probe must not be used with token"); });

        Map<String, Boolean> first = service.check(List.of("alive/repo", "gone/repo", "bad name"));
        Map<String, Boolean> second = service.check(List.of("alive/repo", "gone/repo"));

        assertThat(first).containsExactlyInAnyOrderEntriesOf(Map.of("alive/repo", true, "gone/repo", false));
        assertThat(second).isEqualTo(first);
        assertThat(queries).hasSize(1);
        assertThat(queries.getFirst()).contains("r0: repository(owner: $o0, name: $n0)");
    }

    @Test
    void fallsBackToWebProbeWithoutToken() {
        RepoExistenceService service = new RepoExistenceService(
                body -> { throw new AssertionError("graphql needs a token"); },
                GithubProperties.defaults(),
                GithubHistoryProperties.defaults(),
                GithubHistoryServiceTest.archive(ArchiveProperties.Mode.HTTP),
                name -> name.startsWith("gone/") ? Optional.of(false)
                        : name.startsWith("flaky/") ? Optional.empty() : Optional.of(true));

        assertThat(service.check(List.of("alive/a", "gone/b", "flaky/c")))
                .containsExactlyInAnyOrderEntriesOf(Map.of("alive/a", true, "gone/b", false));
        assertThat(service.enabled()).isTrue();
    }

    @Test
    void disabledInFixtureMode() {
        RepoExistenceService service = new RepoExistenceService(body -> Map.of(),
                GithubProperties.defaults(), GithubHistoryProperties.defaults(),
                GithubHistoryServiceTest.archive(ArchiveProperties.Mode.FIXTURE),
                name -> Optional.of(true));

        assertThat(service.enabled()).isFalse();
    }
}
