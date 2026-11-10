package ru.itmo.ipkn.starguard.core.index;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TopRepoSelectorTest {

    @Test
    void selectsByStarsWithDeterministicTieBreak() {
        Map<String, Integer> stars = Map.of(
                "a/one", 5,
                "a/two", 5,
                "b/three", 3,
                "c/four", 0);

        assertThat(TopRepoSelector.select(stars, 2))
                .containsExactly("a/one", "a/two");
        assertThat(TopRepoSelector.select(stars, 10))
                .containsExactly("a/one", "a/two", "b/three");
        assertThat(TopRepoSelector.select(stars, 1))
                .containsExactly("a/one");
    }
}
