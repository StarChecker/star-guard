package ru.itmo.ipkn.starguard.core.service;

import ru.itmo.ipkn.starguard.core.archive.ArchiveFetchResult;
import ru.itmo.ipkn.starguard.core.domain.RepoRef;
import ru.itmo.ipkn.starguard.core.domain.RepoStats;
import ru.itmo.ipkn.starguard.core.domain.TimeWindow;
import ru.itmo.ipkn.starguard.core.scoring.RepoScore;

import java.util.Objects;

/** Полный отчёт по репозиторию: статистика, score и сведения о загрузке архива. */
public record RepoReport(
        RepoRef repo,
        TimeWindow window,
        RepoStats stats,
        RepoScore score,
        ArchiveFetchResult archive
) {

    public RepoReport {
        Objects.requireNonNull(repo, "repo");
        Objects.requireNonNull(window, "window");
        Objects.requireNonNull(stats, "stats");
        Objects.requireNonNull(score, "score");
        Objects.requireNonNull(archive, "archive");
    }
}
