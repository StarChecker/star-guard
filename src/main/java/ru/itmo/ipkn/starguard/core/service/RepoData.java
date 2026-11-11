package ru.itmo.ipkn.starguard.core.service;

import ru.itmo.ipkn.starguard.core.archive.ArchiveFetchResult;
import ru.itmo.ipkn.starguard.core.domain.StarMark;
import ru.itmo.ipkn.starguard.core.domain.TimeWindow;
import ru.itmo.ipkn.starguard.core.stats.RepoAggregate;

import java.util.List;
import java.util.Objects;

/**
 * Данные репозитория для отчёта: окно, агрегаты, метаданные источника,
 * место в индексе (для дозапроса возраста аккаунтов в C2) и звёзды с акторами
 * (для таймлайна с разметкой подозрительных).
 *
 * @param starRank место в топ-N индекса; null вне индекса
 * @param stars    звёзды окна: кто и когда; пустой список, если не собирались
 */
public record RepoData(
        TimeWindow window,
        RepoAggregate aggregate,
        ArchiveFetchResult archive,
        Integer starRank,
        List<StarMark> stars
) {

    public RepoData {
        Objects.requireNonNull(window, "window");
        Objects.requireNonNull(aggregate, "aggregate");
        Objects.requireNonNull(archive, "archive");
        stars = stars == null ? List.of() : List.copyOf(stars);
    }

    public RepoData(TimeWindow window, RepoAggregate aggregate, ArchiveFetchResult archive) {
        this(window, aggregate, archive, null, List.of());
    }

    public RepoData(TimeWindow window, RepoAggregate aggregate, ArchiveFetchResult archive, Integer starRank) {
        this(window, aggregate, archive, starRank, List.of());
    }

    public RepoData(TimeWindow window, RepoAggregate aggregate, ArchiveFetchResult archive, List<StarMark> stars) {
        this(window, aggregate, archive, null, stars);
    }
}
