package ru.itmo.ipkn.starguard.core.service;

import ru.itmo.ipkn.starguard.core.archive.ArchiveFetchResult;
import ru.itmo.ipkn.starguard.core.domain.DataSource;
import ru.itmo.ipkn.starguard.core.domain.RepoRef;
import ru.itmo.ipkn.starguard.core.domain.RepoStats;
import ru.itmo.ipkn.starguard.core.domain.StarTimeline;
import ru.itmo.ipkn.starguard.core.domain.SuspiciousActor;
import ru.itmo.ipkn.starguard.core.domain.TimeWindow;
import ru.itmo.ipkn.starguard.core.scoring.RepoScore;

import java.util.List;
import java.util.Objects;

/** Полный отчёт по репозиторию: статистика, score, таймлайн звёзд и сведения о загрузке архива. */
public record RepoReport(
        RepoRef repo,
        TimeWindow window,
        RepoStats stats,
        RepoScore score,
        ArchiveFetchResult archive,
        StarTimeline starsTimeline,
        DataSource source,
        List<SuspiciousActor> suspiciousActors,
        int suspiciousActorsTotal
) {

    public RepoReport(RepoRef repo, TimeWindow window, RepoStats stats, RepoScore score,
                      ArchiveFetchResult archive, StarTimeline starsTimeline, DataSource source) {
        this(repo, window, stats, score, archive, starsTimeline, source, List.of(), 0);
    }

    public RepoReport {
        Objects.requireNonNull(repo, "repo");
        Objects.requireNonNull(window, "window");
        Objects.requireNonNull(stats, "stats");
        Objects.requireNonNull(score, "score");
        Objects.requireNonNull(archive, "archive");
        starsTimeline = starsTimeline == null ? StarTimeline.empty(10) : starsTimeline;
        suspiciousActors = List.copyOf(suspiciousActors);
        source = source == null ? DataSource.archive() : source;
    }

    public RepoReport(RepoRef repo, TimeWindow window, RepoStats stats, RepoScore score,
                      ArchiveFetchResult archive, StarTimeline starsTimeline) {
        this(repo, window, stats, score, archive, starsTimeline, null);
    }

    public RepoReport(RepoRef repo, TimeWindow window, RepoStats stats, RepoScore score,
                      ArchiveFetchResult archive) {
        this(repo, window, stats, score, archive, null, null);
    }
}
