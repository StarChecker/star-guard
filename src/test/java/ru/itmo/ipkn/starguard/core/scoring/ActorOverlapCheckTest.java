package ru.itmo.ipkn.starguard.core.scoring;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import ru.itmo.ipkn.starguard.core.config.ActorOverlapProperties;
import ru.itmo.ipkn.starguard.core.domain.DataAvailability;
import ru.itmo.ipkn.starguard.core.domain.EventCounts;
import ru.itmo.ipkn.starguard.core.domain.RepoRef;
import ru.itmo.ipkn.starguard.core.domain.RepoSnapshot;
import ru.itmo.ipkn.starguard.core.domain.StarIndex;
import ru.itmo.ipkn.starguard.core.domain.TimeWindow;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

class ActorOverlapCheckTest {

    private static final TimeWindow WINDOW = new TimeWindow(
            Instant.parse("2026-11-10T00:00:00Z"), Instant.parse("2026-11-10T03:00:00Z"), 3);
    private static final String TARGET = "demo/target";

    private final ActorOverlapCheck check = new ActorOverlapCheck(ActorOverlapProperties.defaults());

    @Test
    void farmScoresHigh() {
        // 30 акторов ставят звёзды целевому и ещё четырём репозиториям фермы
        IndexBuilder index = new IndexBuilder().stars(TARGET, 1, 30);
        for (int i = 1; i <= 4; i++) {
            index.stars("farm/repo-" + i, 1, 30);
        }

        CheckResult result = check.evaluate(snapshot(index.build(3)));

        assertThat(result.checkId()).isEqualTo(CheckIds.ACTOR_OVERLAP);
        assertThat(result.score()).isGreaterThanOrEqualTo(0.9);
        assertThat(result.confidence()).isCloseTo(0.8, within(1e-9));
        assertThat(result.details())
                .containsEntry("mocked", false)
                .containsEntry("star_actors", 30)
                .containsEntry("jaccard_max", 1.0)
                .containsEntry("multi_share", 1.0)
                .containsEntry("multi_repo_actors", 30)
                .containsEntry("overlapping_repos", 4)
                .containsEntry("candidates_compared", 4)
                .containsEntry("index_truncated", false)
                .doesNotContainKey("reason");
        assertThat(topOverlaps(result)).hasSize(3);
        assertThat(topOverlaps(result).getFirst())
                .containsEntry("repo", "farm/repo-1")
                .containsEntry("jaccard", 1.0)
                .containsEntry("shared_actors", 30)
                .containsEntry("repo_star_actors", 30);
    }

    @Test
    void randomOverlapScoresLow() {
        // 40 акторов; со случайным популярным репозиторием общий всего один
        StarIndex index = new IndexBuilder()
                .stars(TARGET, 1, 40)
                .stars("popular/lib", 40, 120)
                .stars("other/tool", 500, 520)
                .build(3);

        CheckResult result = check.evaluate(snapshot(index));

        assertThat(result.score()).isLessThanOrEqualTo(0.1);
        assertThat(result.confidence()).isCloseTo(0.8, within(1e-9));
        assertThat(result.details())
                .containsEntry("overlapping_repos", 1)
                .containsEntry("multi_share", 0.0);
        assertThat((double) result.details().get("jaccard_max")).isLessThan(0.05);
    }

    @Test
    void multiRepoActorsAloneGiveHalfScore() {
        // каждый актор ставит звёзды двум своим «личным» репозиториям: Jaccard мал, multiShare = 1
        IndexBuilder index = new IndexBuilder().stars(TARGET, 1, 20);
        for (int actor = 1; actor <= 20; actor++) {
            index.stars("solo/a-" + actor, actor, actor);
            index.stars("solo/b-" + actor, actor, actor);
        }

        CheckResult result = check.evaluate(snapshot(index.build(3)));

        assertThat(result.details()).containsEntry("multi_share", 1.0).containsEntry("s_jaccard", 0.0);
        assertThat(result.score()).isCloseTo(0.5, within(1e-9));
    }

    @Test
    void actorWithTwoReposIsNotMultiRepo() {
        // все акторы поставили звезду только ещё одному репозиторию: всего 2 < порога 3
        StarIndex index = new IndexBuilder()
                .stars(TARGET, 1, 20)
                .stars("pair/one", 1, 20)
                .build(3);

        CheckResult result = check.evaluate(snapshot(index));

        assertThat(result.details()).containsEntry("multi_share", 0.0).containsEntry("jaccard_max", 1.0);
        assertThat(result.score()).isCloseTo(0.5, within(1e-9));
    }

    @Test
    void smallSetIsGatedAndInactive() {
        StarIndex index = new IndexBuilder()
                .stars(TARGET, 1, 4)
                .stars("farm/a", 1, 4)
                .stars("farm/b", 1, 4)
                .build(3);

        CheckResult result = check.evaluate(snapshot(index));

        assertThat(result.score()).isCloseTo(0.4, within(1e-9));
        assertThat(result.confidence()).isZero();
        assertThat(result.details())
                .containsEntry("gate", 0.4)
                .containsEntry("reason", "too_few_star_actors")
                .containsEntry("min_actors_for_confidence", 5);
    }

    @Test
    void gateScalesScoreBelowActorsGate() {
        StarIndex index = new IndexBuilder()
                .stars(TARGET, 1, 8)
                .stars("farm/a", 1, 8)
                .stars("farm/b", 1, 8)
                .build(3);

        CheckResult result = check.evaluate(snapshot(index));

        assertThat(result.score()).isCloseTo(0.8, within(1e-9));
        assertThat(result.confidence()).isCloseTo(0.8, within(1e-9));
    }

    @Test
    void missingIndexMakesCheckInactive() {
        CheckResult result = check.evaluate(snapshot(StarIndex.empty()));

        assertThat(result.score()).isZero();
        assertThat(result.confidence()).isZero();
        assertThat(result.details()).containsEntry("reason", "no_cross_repo_index");
    }

    @Test
    void repoWithoutStarsInIndexIsInactive() {
        StarIndex index = new IndexBuilder().stars("other/repo", 1, 50).build(3);

        CheckResult result = check.evaluate(snapshot(index));

        assertThat(result.score()).isZero();
        assertThat(result.confidence()).isZero();
        assertThat(result.details())
                .containsEntry("star_actors", 0)
                .containsEntry("reason", "too_few_star_actors");
    }

    @Test
    void partialAndTruncatedIndexLowersConfidence() {
        IndexBuilder partial = new IndexBuilder().stars(TARGET, 1, 30).stars("farm/a", 1, 30);
        CheckResult twoOfThreeHours = check.evaluate(snapshot(partial.build(2)));
        assertThat(twoOfThreeHours.confidence()).isCloseTo(0.8 * 2 / 3, within(1e-9));

        StarIndex.Builder truncated = new StarIndex.Builder(31);
        for (int actor = 1; actor <= 30; actor++) {
            truncated.add(TARGET, actor);
        }
        truncated.add("farm/a", 1);
        truncated.add("farm/a", 2);
        CheckResult truncatedResult = check.evaluate(snapshot(StarIndex.merge(List.of(
                truncated.build(1), new IndexBuilder().stars("x/y", 1000, 1000).build(2)))));

        assertThat(truncatedResult.confidence()).isCloseTo(0.8 * 0.75, within(1e-9));
        assertThat(truncatedResult.details()).containsEntry("index_truncated", true);
    }

    @Test
    void candidatePoolLimitsComparedRepos() {
        ActorOverlapProperties props = new ActorOverlapProperties(
                null, 1, null, null, null, null, null, null, null, null, null, null, null, null);
        StarIndex index = new IndexBuilder()
                .stars(TARGET, 1, 20)
                .stars("big/overlap", 1, 10)
                .stars("small/overlap", 11, 15)
                .build(3);

        CheckResult result = new ActorOverlapCheck(props).evaluate(snapshot(index));

        assertThat(result.details())
                .containsEntry("overlapping_repos", 2)
                .containsEntry("candidates_compared", 1)
                .containsEntry("jaccard_max", 0.5);
        assertThat(topOverlaps(result)).singleElement()
                .satisfies(item -> assertThat(item).containsEntry("repo", "big/overlap"));
    }

    @Test
    void targetRepoNameIsCaseInsensitive() {
        StarIndex index = new IndexBuilder()
                .stars("Demo/Target", 1, 30)
                .stars("farm/a", 1, 30)
                .stars("farm/b", 1, 30)
                .build(3);

        CheckResult result = check.evaluate(snapshot(index));

        assertThat(result.details()).containsEntry("star_actors", 30);
        assertThat(result.score()).isEqualTo(1.0);
    }

    @Test
    void invalidPropertiesFailFast() {
        assertThatThrownBy(() -> new ActorOverlapProperties(
                null, null, null, null, null, 1, null, null, null, null, null, null, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("multi-repo-threshold");
        assertThatThrownBy(() -> new ActorOverlapProperties(
                null, null, null, 0.0, null, null, null, null, null, null, null, null, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("jaccard-span");
    }

    @Test
    void modeBindsFromProperty() {
        new ApplicationContextRunner()
                .withUserConfiguration(PropertiesConfig.class)
                .withPropertyValues(ActorOverlapProperties.MODE_PROPERTY + "=real")
                .run(context -> assertThat(context.getBean(ActorOverlapProperties.class).mode())
                        .isEqualTo(CheckMode.REAL));
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> topOverlaps(CheckResult result) {
        return (List<Map<String, Object>>) result.details().get("top_overlaps");
    }

    private static RepoSnapshot snapshot(StarIndex index) {
        long[] actors = index.actors(TARGET);
        EventCounts counts = new EventCounts(actors.length, actors.length, 0, 0, 0, 0, 0);
        return new RepoSnapshot(new RepoRef("demo", "target"), WINDOW, WINDOW.to(), counts,
                actors.length, actors.length, null,
                DataAvailability.pocDefaults().withCrossRepoOverlap(index.available()), index);
    }

    /** Индекс звёзд: репозиторий ← акторы с id из диапазона [from, to]. */
    private static final class IndexBuilder {

        private final StarIndex.Builder builder = new StarIndex.Builder(1_000_000);

        IndexBuilder stars(String repo, long fromActor, long toActor) {
            for (long actor = fromActor; actor <= toActor; actor++) {
                builder.add(repo, actor);
            }
            return this;
        }

        StarIndex build(int hoursCovered) {
            return builder.build(hoursCovered);
        }
    }

    @EnableConfigurationProperties(ActorOverlapProperties.class)
    static class PropertiesConfig {
    }
}
