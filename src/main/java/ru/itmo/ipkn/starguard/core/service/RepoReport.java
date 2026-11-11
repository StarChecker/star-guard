package ru.itmo.ipkn.starguard.core.service;

import ru.itmo.ipkn.starguard.core.archive.ArchiveFetchResult;
import ru.itmo.ipkn.starguard.core.domain.RepoRef;
import ru.itmo.ipkn.starguard.core.domain.RepoStats;
import ru.itmo.ipkn.starguard.core.domain.StarTimeline;
import ru.itmo.ipkn.starguard.core.domain.TimeWindow;
import ru.itmo.ipkn.starguard.core.scoring.RepoScore;

import java.util.Objects;

/** Полный отчёт по репозиторию: статистика, score, таймлайн звёзд и сведения о загрузке архива. */
public record RepoReport(
        RepoRef repo,
        TimeWindow window,
        RepoStats stats,
        RepoScore score,
        ArchiveFetchResult archive,
        StarTimeline starsTimeline
) {

    public RepoReport {
        Objects.requireNonNull(repo, "repo");
        Objects.requireNonNull(window, "window");
        Objects.requireNonNull(stats, "stats");
        Objects.requireNonNull(score, "score");
        Objects.requireNonNull(archive, "archive");
        starsTimeline = starsTimeline == null ? StarTimeline.empty(10) : starsTimeline;
    }

    public RepoReport(RepoRef repo, TimeWindow window, RepoStats stats, RepoScore score,
                      ArchiveFetchResult archive) {
        this(repo, window, stats, score, archive, null);
    }
}
