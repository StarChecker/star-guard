package ru.itmo.ipkn.starguard.core.index;

import ru.itmo.ipkn.starguard.core.archive.ArchiveFetchResult;
import ru.itmo.ipkn.starguard.core.archive.InvalidWindowException;
import ru.itmo.ipkn.starguard.core.config.ArchiveProperties;
import ru.itmo.ipkn.starguard.core.domain.RepoRef;
import ru.itmo.ipkn.starguard.core.domain.TimeWindow;
import ru.itmo.ipkn.starguard.core.service.RepoData;
import ru.itmo.ipkn.starguard.core.service.RepoDataProvider;
import ru.itmo.ipkn.starguard.core.stats.RepoAggregate;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

/** Отдаёт отчёт из предвычисленного индекса; вне индекса — быстрый 404. */
public class IndexRepoDataProvider implements RepoDataProvider {

    private final RepoIndex repoIndex;
    private final ArchiveProperties archiveProperties;
    private final Clock clock;

    public IndexRepoDataProvider(
            RepoIndex repoIndex,
            ArchiveProperties archiveProperties,
            Clock clock) {
        this.repoIndex = repoIndex;
        this.archiveProperties = archiveProperties;
        this.clock = clock;
    }

    @Override
    public RepoData load(RepoRef repo, Integer requestedHours) {
        RepoIndexSnapshot snapshot = repoIndex.snapshot()
                .orElseThrow(IndexNotReadyException::new);
        int hours = requestedHours == null
                ? archiveProperties.defaultWindowHours()
                : requestedHours;
        if (hours < 1 || hours > snapshot.window().hours()) {
            throw new InvalidWindowException(
                    "hours must be within [1, %d] for the indexed window"
                            .formatted(snapshot.window().hours()));
        }

        IndexedRepo indexed = snapshot.find(repo.fullName())
                .orElseThrow(() -> new RepoNotIndexedException(repo, snapshot.window()));
        TimeWindow window = snapshot.windowFor(hours);
        RepoAggregate aggregate = RepoStatsComposer.compose(
                repo, window, indexed.hours(), clock, snapshot.starIndex(window));

        List<Instant> missing = snapshot.missingHours().stream()
                .filter(hour -> !hour.isBefore(window.from()) && hour.isBefore(window.to()))
                .toList();
        ArchiveFetchResult archive = new ArchiveFetchResult(
                List.of(),
                hours,
                hours - missing.size(),
                missing,
                snapshot.parseErrors(),
                true);
        return new RepoData(window, aggregate, archive, snapshot.rank(repo.fullName()));
    }
}
