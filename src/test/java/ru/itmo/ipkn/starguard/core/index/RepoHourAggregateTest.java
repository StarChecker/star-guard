package ru.itmo.ipkn.starguard.core.index;

import org.junit.jupiter.api.Test;
import ru.itmo.ipkn.starguard.core.domain.EventCounts;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class RepoHourAggregateTest {

    private static final Instant HOUR = Instant.parse("2026-11-10T07:00:00Z");

    @Test
    void sharedZeroSeriesIsNeverExposedForWriting() {
        RepoHourAggregate empty = RepoHourAggregate.empty(HOUR);
        int[] leaked = empty.starsByMinute();
        leaked[5] = 42;

        assertThat(empty.starsByMinute()[5]).isZero();
        assertThat(RepoHourAggregate.empty(HOUR).starsByMinute()[5]).isZero();
        assertThat(new RepoHourAggregate(HOUR, EventCounts.empty(), new int[60], 0, 0).starsByMinute())
                .containsOnly(0);
    }

    @Test
    void nonZeroSeriesIsCopiedOnConstruction() {
        int[] source = new int[60];
        source[3] = 2;
        RepoHourAggregate aggregate = new RepoHourAggregate(
                HOUR, new EventCounts(2, 2, 0, 0, 0, 0, 0), source, 1, 1);

        source[3] = 7;

        assertThat(aggregate.starsByMinute()[3]).isEqualTo(2);
    }
}
