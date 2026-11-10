package ru.itmo.ipkn.starguard.core.domain;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StarIndexTest {

    @Test
    void builderDeduplicatesSortsAndNormalizesRepoNames() {
        StarIndex.Builder builder = new StarIndex.Builder(100);
        builder.add("Demo/Repo", 30);
        builder.add("demo/repo", 10);
        builder.add("demo/repo", 30);
        builder.add("other/repo", 20);

        StarIndex index = builder.build(1);

        assertThat(index.available()).isTrue();
        assertThat(index.actors("DEMO/REPO")).containsExactly(10L, 30L);
        assertThat(index.actors("other/repo")).containsExactly(20L);
        assertThat(index.actors("missing/repo")).isEmpty();
        assertThat(index.repoCount()).isEqualTo(2);
        assertThat(index.pairCount()).isEqualTo(3);
        assertThat(index.truncated()).isFalse();
    }

    @Test
    void builderStopsAtPairLimitAndMarksTruncated() {
        StarIndex.Builder builder = new StarIndex.Builder(2);
        builder.add("demo/repo", 1);
        builder.add("demo/repo", 2);
        builder.add("demo/repo", 3);

        StarIndex index = builder.build(1);

        assertThat(index.actors("demo/repo")).containsExactly(1L, 2L);
        assertThat(index.truncated()).isTrue();
    }

    @Test
    void mergeUnitesHoursAndActors() {
        StarIndex.Builder hour0 = new StarIndex.Builder(100);
        hour0.add("demo/repo", 1);
        hour0.add("demo/repo", 5);
        StarIndex.Builder hour1 = new StarIndex.Builder(1);
        hour1.add("demo/repo", 3);
        hour1.add("demo/repo", 5);
        hour1.add("other/repo", 7);

        StarIndex merged = StarIndex.merge(List.of(hour0.build(1), hour1.build(1)));

        assertThat(merged.hoursCovered()).isEqualTo(2);
        assertThat(merged.truncated()).isTrue();
        // во втором часе лимит в одну пару: принят только актор 3, остальное отброшено
        assertThat(merged.actors("demo/repo")).containsExactly(1L, 3L, 5L);
        assertThat(merged.actors("other/repo")).isEmpty();
    }

    @Test
    void unionSortedKeepsUniqueValues() {
        assertThat(StarIndex.unionSorted(new long[]{1, 3, 5}, new long[]{2, 3, 6}))
                .containsExactly(1L, 2L, 3L, 5L, 6L);
        assertThat(StarIndex.unionSorted(new long[0], new long[]{4})).containsExactly(4L);
    }

    @Test
    void emptyIndexIsUnavailable() {
        assertThat(StarIndex.empty().available()).isFalse();
        assertThat(StarIndex.merge(List.of()).available()).isFalse();
        assertThatThrownBy(() -> new StarIndex.Builder(0)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void actorsReturnsDefensiveCopy() {
        StarIndex.Builder builder = new StarIndex.Builder(10);
        builder.add("demo/repo", 1);
        StarIndex index = builder.build(1);

        index.actors("demo/repo")[0] = 42;

        assertThat(index.actors("demo/repo")).containsExactly(1L);
    }
}
