package ru.itmo.ipkn.starguard.core.index;

import org.junit.jupiter.api.Test;
import ru.itmo.ipkn.starguard.core.domain.RepoRef;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PinnedReposTest {

    @Test
    void pinningIsIdempotentAndCaseInsensitive() {
        PinnedRepos pins = new PinnedRepos(2);

        pins.pin(new RepoRef("Demo", "Repo"));
        pins.pin(new RepoRef("demo", "repo"));

        assertThat(pins.size()).isEqualTo(1);
        assertThat(pins.keys()).containsExactly("demo/repo");
        assertThat(pins.isPinned("DEMO/REPO")).isTrue();
        assertThat(pins.isPinned("other/repo")).isFalse();
    }

    @Test
    void limitRejectsOnlyNewRepositories() {
        PinnedRepos pins = new PinnedRepos(1);
        pins.pin(new RepoRef("a", "one"));

        pins.pin(new RepoRef("A", "One"));

        assertThatThrownBy(() -> pins.pin(new RepoRef("b", "two")))
                .isInstanceOf(PinLimitExceededException.class)
                .hasMessageContaining("app.index.pins.max-pinned");
        assertThat(pins.keys()).containsExactly("a/one");
    }

    @Test
    void noneHasNoPinsAndRejectsNegativeLimit() {
        assertThat(PinnedRepos.none().keys()).isEmpty();
        assertThatThrownBy(() -> new PinnedRepos(-1)).isInstanceOf(IllegalArgumentException.class);
    }
}
