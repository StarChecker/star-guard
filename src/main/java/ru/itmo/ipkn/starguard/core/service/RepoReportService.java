package ru.itmo.ipkn.starguard.core.service;

import org.springframework.stereotype.Service;
import ru.itmo.ipkn.starguard.core.archive.ArchiveFetchResult;
import ru.itmo.ipkn.starguard.core.archive.ArchiveUnavailableException;
import ru.itmo.ipkn.starguard.core.archive.GhArchiveClient;
import ru.itmo.ipkn.starguard.core.archive.TimeWindowCalculator;
import ru.itmo.ipkn.starguard.core.config.ScoringProperties;
import ru.itmo.ipkn.starguard.core.domain.RepoRef;
import ru.itmo.ipkn.starguard.core.domain.TimeWindow;
import ru.itmo.ipkn.starguard.core.scoring.MockMode;
import ru.itmo.ipkn.starguard.core.scoring.MockModeContext;
import ru.itmo.ipkn.starguard.core.scoring.RepoScore;
import ru.itmo.ipkn.starguard.core.scoring.ScoringEngine;
import ru.itmo.ipkn.starguard.core.stats.RepoAggregate;
import ru.itmo.ipkn.starguard.core.stats.StatsAggregator;

/** Оркестрация PoC: архив → статистика → scoring. */
@Service
public class RepoReportService {

    private final GhArchiveClient archiveClient;
    private final TimeWindowCalculator windowCalculator;
    private final StatsAggregator aggregator;
    private final ScoringEngine scoringEngine;
    private final ScoringProperties scoringProperties;
    private final MockModeContext mockModeContext;

    public RepoReportService(
            GhArchiveClient archiveClient,
            TimeWindowCalculator windowCalculator,
            StatsAggregator aggregator,
            ScoringEngine scoringEngine,
            ScoringProperties scoringProperties,
            MockModeContext mockModeContext) {
        this.archiveClient = archiveClient;
        this.windowCalculator = windowCalculator;
        this.aggregator = aggregator;
        this.scoringEngine = scoringEngine;
        this.scoringProperties = scoringProperties;
        this.mockModeContext = mockModeContext;
    }

    public RepoReport getReport(RepoRef repo, Integer requestedHours, MockMode requestedMockMode) {
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
        MockMode mockMode = requestedMockMode == null
                ? scoringProperties.mockMode()
                : requestedMockMode;
        RepoScore score = mockModeContext.callWith(
                mockMode,
                () -> scoringEngine.score(aggregate.snapshot()));

        return new RepoReport(repo, window, aggregate.stats(), score, archive);
    }
}
