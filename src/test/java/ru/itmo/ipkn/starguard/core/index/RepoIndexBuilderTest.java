package ru.itmo.ipkn.starguard.core.index;

import org.junit.jupiter.api.Test;
import ru.itmo.ipkn.starguard.core.archive.GhArchiveBulkReader;
import ru.itmo.ipkn.starguard.core.archive.HourReadResult;
import ru.itmo.ipkn.starguard.core.archive.TimeWindowCalculator;
import ru.itmo.ipkn.starguard.core.config.ArchiveProperties;
import ru.itmo.ipkn.starguard.core.config.RepoIndexProperties;
import ru.itmo.ipkn.starguard.core.domain.EventType;
import ru.itmo.ipkn.starguard.core.domain.GhEvent;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RepoIndexBuilderTest {

    private static final Clock CLOCK =
            Clock.fixed(Instant.parse("2026-11-10T12:00:00Z"), ZoneOffset.UTC);
    private static final Instant H07 = Instant.parse("2026-11-10T07:00:00Z");
    private static final Instant H08 = Instant.parse("2026-11-10T08:00:00Z");
    private static final Instant H09 = Instant.parse("2026-11-10T09:00:00Z");

    private static final ArchiveProperties ARCHIVE = new ArchiveProperties(
            "https://example.test", ArchiveProperties.Mode.HTTP,
            6, 24, 2, 4, 0, false, Duration.ofHours(1), null);
    private static final RepoIndexProperties INDEX = new RepoIndexProperties(
            true, 2, 3, false, "0 10 * * * *");

    @Test
    void buildsTopRepositoriesAndHourlyAggregates() {
        FakeBulkReader reader = new FakeBulkReader();
        reader.add(H07, event("1", EventType.WATCH, "a/one", 1, H07, 10));
        reader.add(H07, event("2", EventType.WATCH, "a/one", 2, H07, 20));
        reader.add(H07, event("3", EventType.WATCH, "b/three", 3, H07, 30));
        reader.add(H07, event("4", EventType.WATCH, "c/four", 4, H07, 40));
        reader.add(H08, event("5", EventType.WATCH, "a/one", 1, H08, 10));
        reader.add(H08, event("6", EventType.WATCH, "a/one", 2, H08, 20));
        reader.add(H08, event("7", EventType.WATCH, "b/three", 3, H08, 30));
        reader.add(H09, event("8", EventType.WATCH, "a/one", 1, H09, 10));
        reader.add(H09, event("9", EventType.WATCH, "a/two", 5, H09, 20));
        reader.add(H09, event("10", EventType.PUSH, "a/one", 6, H09, 30));

        RepoIndexSnapshot snapshot = builder(reader).build();

        assertThat(snapshot.window().hours()).isEqualTo(3);
        assertThat(snapshot.hoursLoaded()).isEqualTo(3);
        assertThat(snapshot.missingHours()).isEmpty();
        assertThat(snapshot.repos()).containsOnlyKeys("a/one", "b/three");
        IndexedRepo top = snapshot.find("A/ONE").orElseThrow();
        assertThat(RepoIndexSnapshot.totalStars(top)).isEqualTo(5);
        assertThat(top.hours()).hasSize(3);
        assertThat(top.hours().get(0).counts().stars()).isEqualTo(2);
        assertThat(top.hours().get(1).counts().stars()).isEqualTo(2);
        assertThat(top.hours().get(2).counts().stars()).isEqualTo(1);
        assertThat(top.hours().get(2).counts().pushes()).isEqualTo(1);
        assertThat(top.hours().stream().mapToInt(hour -> hour.counts().total()).sum())
                .isEqualTo(6);
    }

    @Test
    void keepsBuildingWhenAnHourIsMissing() {
        FakeBulkReader reader = new FakeBulkReader();
        reader.add(H07, event("1", EventType.WATCH, "a/one", 1, H07, 10));
        reader.add(H07, event("2", EventType.WATCH, "b/three", 2, H07, 20));
        reader.add(H09, event("3", EventType.WATCH, "a/one", 1, H09, 10));
        reader.missing(H08);

        RepoIndexSnapshot snapshot = builder(reader).build();

        assertThat(snapshot.hoursLoaded()).isEqualTo(2);
        assertThat(snapshot.missingHours()).containsExactly(H08);
        assertThat(snapshot.repos()).containsKeys("a/one", "b/three");
    }

    @Test
    void failsWhenNoHoursLoaded() {
        FakeBulkReader reader = new FakeBulkReader();
        reader.missing(H07);
        reader.missing(H08);
        reader.missing(H09);

        assertThatThrownBy(() -> builder(reader).build())
                .isInstanceOf(IndexBuildException.class)
                .hasMessageContaining("No WatchEvent");
    }

    private static RepoIndexBuilder builder(GhArchiveBulkReader reader) {
        return new RepoIndexBuilder(
                reader,
                new TimeWindowCalculator(CLOCK, ARCHIVE),
                ARCHIVE,
                INDEX,
                CLOCK);
    }

    private static GhEvent event(
            String id, EventType type, String repo, long actor, Instant hour, int minute) {
        return new GhEvent(id, type, 1L, repo, actor, "dev" + actor,
                hour.plus(Duration.ofMinutes(minute)));
    }

    private static final class FakeBulkReader implements GhArchiveBulkReader {

        private final Map<Instant, List<GhEvent>> events = new HashMap<>();
        private final Set<Instant> missing = new HashSet<>();

        private void add(Instant hour, GhEvent event) {
            events.computeIfAbsent(hour, ignored -> new ArrayList<>()).add(event);
        }

        private void missing(Instant hour) {
            missing.add(hour);
        }

        @Override
        public HourReadResult readHour(Instant hour, Consumer<GhEvent> sink) {
            if (missing.contains(hour)) {
                return new HourReadResult(hour, 0, true, false);
            }
            events.getOrDefault(hour, List.of()).forEach(sink);
            return new HourReadResult(hour, 0, false, false);
        }
    }
}
