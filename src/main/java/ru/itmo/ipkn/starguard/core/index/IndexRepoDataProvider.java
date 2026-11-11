package ru.itmo.ipkn.starguard.core.index;

import ru.itmo.ipkn.starguard.core.archive.ArchiveFetchResult;
import ru.itmo.ipkn.starguard.core.archive.InvalidWindowException;
import ru.itmo.ipkn.starguard.core.config.ArchiveProperties;
import ru.itmo.ipkn.starguard.core.domain.DataSource;
import ru.itmo.ipkn.starguard.core.domain.RepoRef;
import ru.itmo.ipkn.starguard.core.domain.StarIndex;
import ru.itmo.ipkn.starguard.core.domain.StarMark;
import ru.itmo.ipkn.starguard.core.domain.TimeWindow;
import ru.itmo.ipkn.starguard.core.service.RepoData;
import ru.itmo.ipkn.starguard.core.service.RepoDataProvider;
import ru.itmo.ipkn.starguard.core.stats.RepoAggregate;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Отдаёт отчёт из предвычисленного индекса: топ-N или long-tail окна (любой репозиторий
 * с событиями в окне). Если событий в окне не было — быстрый 404.
 */
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

        // Топ-N, а вне топа — long-tail окна из почасовых дайджестов (F4-1).
        IndexHit hit = snapshot.lookup(repo)
                .orElseThrow(() -> new RepoNotIndexedException(repo, snapshot.window()));
        TimeWindow window = snapshot.windowFor(hours);
        RepoAggregate aggregate = RepoStatsComposer.compose(
                repo, window, hit.repo().hours(), clock, snapshot.starIndex(window));

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
        // Место в топе нужно только топу: по нему решается, дозапрашивать ли возраст аккаунтов (C2).
        Integer rank = hit.tier() == DataSource.Tier.TOP ? snapshot.rank(repo.fullName()) : null;
        return new RepoData(window, aggregate, archive, rank,
                starMarks(snapshot, repo, window), DataSource.index(hit.tier()));
    }

    /** Звёзды подокна с точностью до часа: индекс хранит акторов по часам. */
    private static List<StarMark> starMarks(RepoIndexSnapshot snapshot, RepoRef repo, TimeWindow window) {
        List<StarMark> marks = new ArrayList<>();
        for (Instant hour : window.hourStarts()) {
            StarIndex hourIndex = snapshot.starIndexByHour().get(hour);
            if (hourIndex == null) {
                continue;
            }
            for (long actor : hourIndex.actors(repo.fullName())) {
                marks.add(new StarMark(hour, actor, true));
            }
        }
        return marks;
    }
}
