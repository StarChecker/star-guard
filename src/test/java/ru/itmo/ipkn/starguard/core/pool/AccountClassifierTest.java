package ru.itmo.ipkn.starguard.core.pool;

import org.junit.jupiter.api.Test;
import ru.itmo.ipkn.starguard.core.github.GithubAccountPoolClient.Profile;
import ru.itmo.ipkn.starguard.core.github.GithubAccountPoolClient.Star;

import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static ru.itmo.ipkn.starguard.core.pool.PoolFixtures.*;

class AccountClassifierTest {
    @Test
    void requiresVolumeAndRepeatedBatchesAlongsideLowActivity() {
        assertThat(AccountClassifier.score(bursty("U1"), defaults())).isEqualTo(1.0);
        var p = bursty("U1");
        assertThat(AccountClassifier.score(new Profile(p.id(), 0, 0, 0, 999, p.stars()), defaults())).isZero();
        assertThat(AccountClassifier.score(new Profile(p.id(), 2, 0, 0, 5000, p.stars()), defaults())).isZero();
        assertThat(AccountClassifier.score(new Profile(p.id(), 0, 6, 0, 5000, p.stars()), defaults())).isZero();
        assertThat(AccountClassifier.score(new Profile(p.id(), 0, 0, 3, 5000, p.stars()), defaults())).isZero();
    }

    @Test
    void emptyProfileWithManyEvenlySpacedStarsIsNotEnough() {
        var stars = IntStream.range(0, 100).mapToObj(i ->
                new Star("R" + i, "a/b" + i, NOW.minusSeconds(i * 120L))).toList();
        assertThat(AccountClassifier.score(new Profile("U1", 0, 0, 0, 10000, stars), defaults())).isZero();
    }

    @Test
    void oneBatchOrSmallSampleIsNotEnough() {
        var stars = IntStream.range(0, 100).mapToObj(i ->
                new Star("R" + i, "a/b" + i, NOW.minusSeconds(1))).toList();
        assertThat(AccountClassifier.score(new Profile("U1", 0, 0, 0, 5000, stars), defaults())).isZero();
        assertThat(AccountClassifier.score(new Profile("U1", 0, 0, 0, 5000,
                bursty("U1").stars().subList(0, 10)), defaults())).isZero();
    }
}
