package ru.itmo.ipkn.starguard.core.index;

import org.junit.jupiter.api.Test;
import ru.itmo.ipkn.starguard.core.archive.HourReadResult;
import ru.itmo.ipkn.starguard.core.domain.DataSource;
import ru.itmo.ipkn.starguard.core.domain.EventCounts;
import ru.itmo.ipkn.starguard.core.domain.RepoRef;
import ru.itmo.ipkn.starguard.core.domain.StarIndex;
import ru.itmo.ipkn.starguard.core.domain.TimeWindow;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IndexAdmissionTest {

    private static final Instant H08 = Instant.parse("2026-11-10T08:00:00Z");
    private static final Instant H09 = Instant.parse("2026-11-10T09:00:00Z");
    private static final RepoRef TOP = new RepoRef("demo", "top");
    private static final RepoRef TAIL = new RepoRef("Tail", "Repo");
    private static final RepoRef COLD = new RepoRef("cold", "repo");

    @Test
    void longTailRepositoryIsPinnedAndPromotedToTop() {
        RepoIndex index = new RepoIndex();
        index.swap(snapshot());
        PinnedRepos pins = new PinnedRepos(10);
        IndexAdmission admission = new IndexAdmission(index, pins);

        assertThat(admission.admit(TAIL)).isEqualTo(IndexAdmission.Outcome.INDEXED);
        assertThat(admission.admit(new RepoRef("tail", "repo"))).isEqualTo(IndexAdmission.Outcome.INDEXED);

        RepoIndexSnapshot promoted = index.snapshot().orElseThrow();
        assertThat(pins.keys()).containsExactly("tail/repo");
        assertThat(promoted.repos()).containsKeys("demo/top", "tail/repo");
        assertThat(promoted.lookup(TAIL).orElseThrow().tier()).isEqualTo(DataSource.Tier.TOP);
        assertThat(RepoIndexSnapshot.totalStars(promoted.find("tail/repo").orElseThrow())).isEqualTo(1);
        assertThat(promoted.digests()).hasSize(2);
    }

    @Test
    void topRepositoryIsPinnedWithoutChangingSnapshot() {
        RepoIndex index = new RepoIndex();
        RepoIndexSnapshot snapshot = snapshot();
        index.swap(snapshot);
        PinnedRepos pins = new PinnedRepos(10);

        assertThat(new IndexAdmission(index, pins).admit(TOP)).isEqualTo(IndexAdmission.Outcome.INDEXED);

        assertThat(index.snapshot()).containsSame(snapshot);
        assertThat(pins.isPinned("demo/top")).isTrue();
    }

    @Test
    void repositoryWithoutEventsIsPinnedButNeedsScan() {
        RepoIndex index = new RepoIndex();
        index.swap(snapshot());
        PinnedRepos pins = new PinnedRepos(10);

        assertThat(new IndexAdmission(index, pins).admit(COLD))
                .isEqualTo(IndexAdmission.Outcome.NOT_IN_WINDOW);
        assertThat(pins.isPinned("cold/repo")).isTrue();
        assertThat(index.snapshot().orElseThrow().repos()).containsOnlyKeys("demo/top");
    }

    @Test
    void failsWhenIndexIsWarmingUpOrPinsAreFull() {
        assertThatThrownBy(() -> new IndexAdmission(new RepoIndex(), new PinnedRepos(10)).admit(TAIL))
                .isInstanceOf(IndexNotReadyException.class);

        RepoIndex index = new RepoIndex();
        index.swap(snapshot());
        assertThatThrownBy(() -> new IndexAdmission(index, new PinnedRepos(0)).admit(TAIL))
                .isInstanceOf(PinLimitExceededException.class);
        assertThat(index.snapshot().orElseThrow().repos()).containsOnlyKeys("demo/top");
    }

    private static RepoIndexSnapshot snapshot() {
        TimeWindow window = new TimeWindow(H08, H09.plusSeconds(3600), 2);
        RepoHourAggregate topHour = new RepoHourAggregate(H08, new EventCounts(5, 5, 0, 0, 0, 0, 0), new int[60], 5, 5);
        IndexedRepo top = new IndexedRepo(TOP, List.of(topHour, RepoHourAggregate.empty(H09)));
        RepoHourAggregate tailHour = new RepoHourAggregate(H09, new EventCounts(2, 1, 0, 0, 1, 0, 0), new int[60], 2, 1);
        HourDigest digest08 = new HourDigest(H08, new HourReadResult(H08, 0, false, false),
                Map.of("demo/top", topHour), Map.of("demo/top", "demo/top"), StarIndex.empty());
        HourDigest digest09 = new HourDigest(H09, new HourReadResult(H09, 0, false, false),
                Map.of("tail/repo", tailHour), Map.of("tail/repo", "Tail/Repo"), StarIndex.empty());
        return new RepoIndexSnapshot(window, Instant.parse("2026-11-10T12:00:00Z"), 2, List.of(), 0,
                Map.of("demo/top", top), Map.of(), Map.of(H08, digest08, H09, digest09));
    }
}
