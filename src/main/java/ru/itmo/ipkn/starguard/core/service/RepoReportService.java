package ru.itmo.ipkn.starguard.core.service;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import ru.itmo.ipkn.starguard.core.archive.ArchiveFetchResult;
import ru.itmo.ipkn.starguard.core.archive.ArchiveUnavailableException;
import ru.itmo.ipkn.starguard.core.archive.GhArchiveClient;
import ru.itmo.ipkn.starguard.core.archive.TimeWindowCalculator;
import ru.itmo.ipkn.starguard.core.config.ScoringProperties;
import ru.itmo.ipkn.starguard.core.domain.RepoRef;
import ru.itmo.ipkn.starguard.core.domain.TimeWindow;
import ru.itmo.ipkn.starguard.core.github.AccountAgeEnricher;
import ru.itmo.ipkn.starguard.core.scoring.MockMode;
import ru.itmo.ipkn.starguard.core.scoring.MockModeContext;
import ru.itmo.ipkn.starguard.core.scoring.RepoScore;
import ru.itmo.ipkn.starguard.core.scoring.ScoringEngine;
import ru.itmo.ipkn.starguard.core.stats.RepoAggregate;
import ru.itmo.ipkn.starguard.core.stats.StatsAggregator;

/**
 * Оркестрация PoC: данные из индекса (быстрый путь) или прямо из GH Archive
 * (fixture-режим / индекс отключён) → scoring.
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
    private final AccountAgeEnricher accountAgeEnricher;

    public RepoReportService(
            GhArchiveClient archiveClient,
            TimeWindowCalculator windowCalculator,
            StatsAggregator aggregator,
            ScoringEngine scoringEngine,
            ScoringProperties scoringProperties,
            MockModeContext mockModeContext,
            ObjectProvider<RepoDataProvider> indexedDataProvider,
            AccountAgeEnricher accountAgeEnricher) {
        this.archiveClient = archiveClient;
        this.windowCalculator = windowCalculator;
        this.aggregator = aggregator;
        this.scoringEngine = scoringEngine;
        this.scoringProperties = scoringProperties;
        this.mockModeContext = mockModeContext;
        this.indexedDataProvider = indexedDataProvider;
        this.accountAgeEnricher = accountAgeEnricher;
    }

    public RepoReport getReport(RepoRef repo, Integer requestedHours, MockMode requestedMockMode) {
        RepoDataProvider indexed = indexedDataProvider.getIfAvailable();
        if (indexed != null) {
            return buildReport(repo, indexed.load(repo, requestedHours), requestedMockMode);
        }
        return getArchiveReport(repo, requestedHours, requestedMockMode);
    }

    private RepoReport getArchiveReport(
            RepoRef repo, Integer requestedHours, MockMode requestedMockMode) {
        TimeWindow window = windowCalculator.window(requestedHours);
        ArchiveFetchResult archive = archiveClient.fetchEvents(repo, window);

        if (archive.events().isEmpty()) {
            if (archive.hoursLoaded() == 0) {
                throw new ArchiveUnavailableException(
                        "GH Archive returned no usable hours for " + repo.fullName());
            }
            throw new RepoNotFoundException(repo, window);
        }

        RepoAggregate aggregate = aggregator.aggregate(repo, window, archive.events(), archive.starIndex());
        return buildReport(repo, new RepoData(window, aggregate, archive), requestedMockMode);
    }

    private RepoReport buildReport(
            RepoRef repo, RepoData data, MockMode requestedMockMode) {
        MockMode mockMode = requestedMockMode == null
                ? scoringProperties.mockMode()
                : requestedMockMode;
        RepoAggregate aggregate = accountAgeEnricher.enrich(data.aggregate(), data.starRank());
        RepoScore score = mockModeContext.callWith(
                mockMode,
                () -> scoringEngine.score(aggregate.snapshot()));
        return new RepoReport(
                repo, data.window(), aggregate.stats(), score, data.archive());
    }
}
