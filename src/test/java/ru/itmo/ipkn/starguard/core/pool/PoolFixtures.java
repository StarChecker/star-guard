package ru.itmo.ipkn.starguard.core.pool;

import ru.itmo.ipkn.starguard.core.config.AccountPoolProperties;
import ru.itmo.ipkn.starguard.core.github.GithubAccountPoolClient.Profile;
import ru.itmo.ipkn.starguard.core.github.GithubAccountPoolClient.Star;

import java.time.Instant;
import java.util.stream.IntStream;

final class PoolFixtures {
    static final Instant NOW = Instant.parse("2026-11-12T12:00:00Z");

    static AccountPoolProperties defaults() {
        return new AccountPoolProperties(null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null);
    }

    static Profile bursty(String id) {
        return new Profile(id, 0, 0, 0, 5000, IntStream.range(0, 100)
                .mapToObj(i -> new Star("R" + i, "owner/repo" + i,
                        NOW.minusSeconds(3600).plusSeconds((i / 20) * 60L + i % 20))).toList());
    }
}
