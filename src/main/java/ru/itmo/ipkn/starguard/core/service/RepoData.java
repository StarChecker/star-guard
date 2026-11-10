package ru.itmo.ipkn.starguard.core.service;

import ru.itmo.ipkn.starguard.core.archive.ArchiveFetchResult;
import ru.itmo.ipkn.starguard.core.domain.TimeWindow;
import ru.itmo.ipkn.starguard.core.stats.RepoAggregate;

import java.util.Objects;

/** Данные репозитория для отчёта: окно, агрегаты и метаданные источника. */
public record RepoData(
        TimeWindow window,
        RepoAggregate aggregate,
        ArchiveFetchResult archive
) {

    public RepoData {
        Objects.requireNonNull(window, "window");
        Objects.requireNonNull(aggregate, "aggregate");
        Objects.requireNonNull(archive, "archive");
    }
}
