package ru.itmo.ipkn.starguard.core.tracker;

import org.junit.jupiter.api.Test;
import ru.itmo.ipkn.starguard.core.domain.GithubSignals;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class IntradayAssemblerTest {

    private static final Instant NOW = Instant.parse("2026-11-12T10:32:00Z");

    @Test
    void placesSamplesOnFiveMinuteGridAndFillsCurrentHoursFromRawSamples() {
        List<TrackerStore.Point> raw = List.of(
                new TrackerStore.Point(Instant.parse("2026-11-12T10:20:04Z"), 1000, 10),
                new TrackerStore.Point(Instant.parse("2026-11-12T10:25:03Z"), 1004, 10),
                new TrackerStore.Point(Instant.parse("2026-11-12T10:30:05Z"), 1009, 11));
        List<TrackerStore.Point> hourly = List.of(
                new TrackerStore.Point(Instant.parse("2026-11-05T10:00:00Z"), 400, 5),
                new TrackerStore.Point(Instant.parse("2026-11-12T09:00:00Z"), 990, 9));

        GithubSignals.Intraday intraday = IntradayAssembler.assemble(NOW, raw, hourly, 7, new double[24],
                Instant.parse("2026-11-01T00:00:00Z"));

        assertThat(intraday.from()).isEqualTo(Instant.parse("2026-11-11T10:25:00Z"));
        assertThat(intraday.stepMinutes()).isEqualTo(5);
        int[] stars = intraday.stars();
        assertThat(stars).hasSize(290);
        assertThat(stars[287]).isEqualTo(1000);
        assertThat(stars[288]).isEqualTo(1004);
        assertThat(stars[289]).isEqualTo(1009);
        assertThat(stars[0]).isEqualTo(GithubSignals.Intraday.MISSING);

        assertThat(intraday.hourlyFrom()).isEqualTo(Instant.parse("2026-11-05T10:00:00Z"));
        int[] hours = intraday.hourlyStars();
        assertThat(hours).hasSize(169);
        assertThat(hours[0]).isEqualTo(400);
        assertThat(hours[167]).isEqualTo(990);
        assertThat(hours[168]).isEqualTo(1009);
        assertThat(intraday.trackedSince()).isEqualTo(Instant.parse("2026-11-01T00:00:00Z"));
    }
}
