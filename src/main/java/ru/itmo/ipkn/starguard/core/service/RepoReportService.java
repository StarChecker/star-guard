package ru.itmo.ipkn.starguard.core.service;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import ru.itmo.ipkn.starguard.core.archive.ArchiveFetchResult;
import ru.itmo.ipkn.starguard.core.archive.ArchiveProgress;
import ru.itmo.ipkn.starguard.core.archive.ArchiveUnavailableException;
import ru.itmo.ipkn.starguard.core.archive.GhArchiveClient;
import ru.itmo.ipkn.starguard.core.archive.TimeWindowCalculator;
import ru.itmo.ipkn.starguard.core.config.ScoringProperties;
import ru.itmo.ipkn.starguard.core.domain.EventType;
import ru.itmo.ipkn.starguard.core.domain.RepoRef;
import ru.itmo.ipkn.starguard.core.domain.StarMark;
import ru.itmo.ipkn.starguard.core.domain.StarTimeline;
import ru.itmo.ipkn.starguard.core.domain.TimeWindow;
import ru.itmo.ipkn.starguard.core.scoring.MockMode;
import ru.itmo.ipkn.starguard.core.scoring.MockModeContext;
import ru.itmo.ipkn.starguard.core.scoring.RepoScore;
import ru.itmo.ipkn.starguard.core.scoring.ScoringEngine;
import ru.itmo.ipkn.starguard.core.stats.RepoAggregate;
import ru.itmo.ipkn.starguard.core.stats.StatsAggregator;

import java.util.List;

/**
 * Оркестрация PoC: данные из индекса (быстрый путь) или прямо из GH Archive
 * (fixture-режим / индекс отключён / скан по запросу) → scoring → таймлайн звёзд.
 */
@Service
public class RepoReportService {

    private final GhArchiveClient archiveClient;
    private final TimeWindowCalculator windowCalculator;
    private final StatsAggregator aggregator;
    private final ScoringEngine scoringEngine;
    private final ScoringProperties scoringProperties;
    private final MockModeContext mockModeContext;
    private final ObjectProvider<RepoDataProvider> indexedDataProvider;
    private final StarTimelineService timelineService;

    public RepoReportService(
            GhArchiveClient archiveClient,
            TimeWindowCalculator windowCalculator,
            StatsAggregator aggregator,
            ScoringEngine scoringEngine,
            ScoringProperties scoringProperties,
            MockModeContext mockModeContext,
            ObjectProvider<RepoDataProvider> indexedDataProvider,
            StarTimelineService timelineService) {
        this.archiveClient = archiveClient;
        this.windowCalculator = windowCalculator;
        this.aggregator = aggregator;
        this.scoringEngine = scoringEngine;
        this.scoringProperties = scoringProperties;
        this.mockModeContext = mockModeContext;
        this.indexedDataProvider = indexedDataProvider;
        this.timelineService = timelineService;
    }

    public RepoReport getReport(RepoRef repo, Integer requestedHours, MockMode requestedMockMode) {
        RepoDataProvider indexed = indexedDataProvider.getIfAvailable();
        if (indexed != null) {
            return buildReport(repo, indexed.load(repo, requestedHours), requestedMockMode);
        }
        return scanArchive(repo, requestedHours, requestedMockMode, ArchiveProgress.NONE);
    }

    /** Есть ли быстрый путь (индекс): тогда пакетные запросы и бейджи не качают дампы. */
    public boolean hasIndex() {
        return indexedDataProvider.getIfAvailable() != null;
    }

    /**
     * Прямой скан GH Archive в обход индекса — для репозиториев вне топ-N (кнопка «проверить»).
     */
    public RepoReport scanArchive(
            RepoRef repo, Integer requestedHours, MockMode requestedMockMode, ArchiveProgress progress) {
        TimeWindow window = windowCalculator.window(requestedHours);
        ArchiveFetchResult archive = archiveClient.fetchEvents(repo, window, progress);

        if (archive.events().isEmpty()) {
            if (archive.hoursLoaded() == 0) {
                throw new ArchiveUnavailableException(
                        "GH Archive returned no usable hours for " + repo.fullName());
            }
            throw new RepoNotFoundException(repo, window);
        }

        RepoAggregate aggregate = aggregator.aggregate(repo, window, archive.events(), archive.starIndex());
        List<StarMark> stars = archive.events().stream()
                .filter(event -> event.type() == EventType.WATCH)
                .map(event -> new StarMark(event.createdAt(), event.actorId(), false))
                .toList();
        return buildReport(repo, new RepoData(window, aggregate, archive, stars), requestedMockMode);
    }

    private RepoReport buildReport(
            RepoRef repo, RepoData data, MockMode requestedMockMode) {
        MockMode mockMode = requestedMockMode == null
                ? scoringProperties.mockMode()
                : requestedMockMode;
        RepoScore score = mockModeContext.callWith(
                mockMode,
                () -> scoringEngine.score(data.aggregate().snapshot()));
        StarTimeline timeline = timelineService.build(
                data.aggregate().snapshot(), score, data.stars());
        return new RepoReport(
                repo, data.window(), data.aggregate().stats(), score, data.archive(), timeline);
    }
}
