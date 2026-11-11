package ru.itmo.ipkn.starguard.core.index;

import org.junit.jupiter.api.Test;
import ru.itmo.ipkn.starguard.core.archive.InvalidWindowException;
import ru.itmo.ipkn.starguard.core.config.ArchiveProperties;
import ru.itmo.ipkn.starguard.core.domain.EventCounts;
import ru.itmo.ipkn.starguard.core.domain.RepoRef;
import ru.itmo.ipkn.starguard.core.domain.StarIndex;
import ru.itmo.ipkn.starguard.core.domain.TimeWindow;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IndexRepoDataProviderTest {

    private static final Clock CLOCK =
            Clock.fixed(Instant.parse("2026-11-10T12:00:00Z"), ZoneOffset.UTC);
    private static final Instant H08 = Instant.parse("2026-11-10T08:00:00Z");
    private static final Instant H09 = Instant.parse("2026-11-10T09:00:00Z");
    private static final Instant H10 = Instant.parse("2026-11-10T10:00:00Z");
    private static final RepoRef REPO = new RepoRef("demo", "star-guard");
    private static final ArchiveProperties ARCHIVE = new ArchiveProperties(
            "https://example.test", ArchiveProperties.Mode.HTTP,
            6, 24, 2, 4, 0, false, Duration.ofHours(1), null);

    @Test
    void composesRequestedSubWindow() {
        RepoIndex index = new RepoIndex();
        index.swap(snapshot());
        IndexRepoDataProvider provider = new IndexRepoDataProvider(index, ARCHIVE, CLOCK);

        var data = provider.load(REPO, 2);

        assertThat(data.window().hours()).isEqualTo(2);
        assertThat(data.window().from()).isEqualTo(H08);
        assertThat(data.window().to()).isEqualTo(H10);
        assertThat(data.aggregate().stats().counts().stars()).isEqualTo(2);
        assertThat(data.archive().hoursRequested()).isEqualTo(2);
        assertThat(data.archive().hoursLoaded()).isEqualTo(2);
        assertThat(data.archive().cached()).isTrue();
        assertThat(data.starRank()).isEqualTo(1);
    }

    @Test
    void passesStarIndexOfSubWindowToSnapshot() {
        StarIndex.Builder hour08 = new StarIndex.Builder(100);
        hour08.add("demo/star-guard", 1);
        hour08.add("farm/other", 1);
        StarIndex.Builder hour09 = new StarIndex.Builder(100);
        hour09.add("demo/star-guard", 2);
        RepoIndexSnapshot base = snapshot();
        RepoIndexSnapshot withStars = new RepoIndexSnapshot(
                base.window(), base.builtAt(), base.hoursLoaded(), base.missingHours(),
                base.parseErrors(), base.repos(), Map.of(H08, hour08.build(1), H09, hour09.build(1)));
        RepoIndex index = new RepoIndex();
        index.swap(withStars);
        IndexRepoDataProvider provider = new IndexRepoDataProvider(index, ARCHIVE, CLOCK);

        var full = provider.load(REPO, 2).aggregate().snapshot();
        var lastHour = provider.load(REPO, 1).aggregate().snapshot();

        assertThat(full.data().crossRepoOverlap()).isTrue();
        assertThat(full.starIndex().hoursCovered()).isEqualTo(2);
        assertThat(full.starIndex().actors("demo/star-guard")).containsExactly(1L, 2L);
        assertThat(full.starIndex().actors("farm/other")).containsExactly(1L);
        assertThat(lastHour.starIndex().hoursCovered()).isEqualTo(1);
        assertThat(lastHour.starIndex().actors("farm/other")).isEmpty();
    }

    @Test
    void snapshotWithoutStarIndexDisablesOverlap() {
        RepoIndex index = new RepoIndex();
        index.swap(snapshot());
        IndexRepoDataProvider provider = new IndexRepoDataProvider(index, ARCHIVE, CLOCK);

        var snapshot = provider.load(REPO, 2).aggregate().snapshot();

        assertThat(snapshot.data().crossRepoOverlap()).isFalse();
        assertThat(snapshot.starIndex().available()).isFalse();
    }

    @Test
    void rejectsUnknownRepository() {
        RepoIndex index = new RepoIndex();
        index.swap(snapshot());
        IndexRepoDataProvider provider = new IndexRepoDataProvider(index, ARCHIVE, CLOCK);

        assertThatThrownBy(() -> provider.load(new RepoRef("unknown", "repo"), 2))
                .isInstanceOf(RepoNotIndexedException.class)
                .hasMessageContaining("/api/v1/index/repos");
    }

    @Test
    void rejectsNotReadyIndexAndTooLargeWindow() {
        RepoIndex empty = new RepoIndex();
        IndexRepoDataProvider notReady = new IndexRepoDataProvider(empty, ARCHIVE, CLOCK);
        assertThatThrownBy(() -> notReady.load(REPO, 2))
                .isInstanceOf(IndexNotReadyException.class);

        RepoIndex index = new RepoIndex();
        index.swap(snapshot());
        IndexRepoDataProvider provider = new IndexRepoDataProvider(index, ARCHIVE, CLOCK);
        assertThatThrownBy(() -> provider.load(REPO, 4))
                .isInstanceOf(InvalidWindowException.class);
    }

    private static RepoIndexSnapshot snapshot() {
        RepoHourAggregate hour08 = new RepoHourAggregate(
                H08, new EventCounts(1, 1, 0, 0, 0, 0, 0), new int[60], 1, 1);
        RepoHourAggregate hour09 = new RepoHourAggregate(
                H09, new EventCounts(1, 1, 0, 0, 0, 0, 0), new int[60], 1, 1);
        TimeWindow window = new TimeWindow(H08, H10, 2);
        IndexedRepo indexed = new IndexedRepo(REPO, List.of(hour08, hour09));
        return new RepoIndexSnapshot(
                window, CLOCK.instant(), 2, List.of(), 0, Map.of("demo/star-guard", indexed));
    }
}
