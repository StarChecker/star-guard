package ru.itmo.ipkn.starguard.core.api;

import org.springframework.stereotype.Component;
import ru.itmo.ipkn.starguard.core.api.dto.ArchiveDto;
import ru.itmo.ipkn.starguard.core.api.dto.CheckDto;
import ru.itmo.ipkn.starguard.core.api.dto.HourBucketDto;
import ru.itmo.ipkn.starguard.core.api.dto.RepoDto;
import ru.itmo.ipkn.starguard.core.api.dto.RepoReportDto;
import ru.itmo.ipkn.starguard.core.api.dto.ScoreDto;
import ru.itmo.ipkn.starguard.core.api.dto.SourceDto;
import ru.itmo.ipkn.starguard.core.api.dto.StarTimelineDto;
import ru.itmo.ipkn.starguard.core.api.dto.StatsDto;
import ru.itmo.ipkn.starguard.core.api.dto.WindowDto;
import ru.itmo.ipkn.starguard.core.domain.EventCounts;
import ru.itmo.ipkn.starguard.core.domain.RepoStats;
import ru.itmo.ipkn.starguard.core.domain.StarTimeline;
import ru.itmo.ipkn.starguard.core.scoring.CheckContribution;
import ru.itmo.ipkn.starguard.core.scoring.RepoScore;
import ru.itmo.ipkn.starguard.core.service.RepoReport;

import java.util.Locale;

/** Домен → REST DTO. */
@Component
public class RepoReportMapper {

    public RepoReportDto toDto(RepoReport report) {
        RepoStats stats = report.stats();
        EventCounts counts = stats.counts();
        RepoScore score = report.score();

        return new RepoReportDto(
                new RepoDto(report.repo().owner(), report.repo().name(), report.repo().fullName()),
                new WindowDto(report.window().from(), report.window().to(), report.window().hours()),
                new StatsDto(
                        counts.total(),
                        counts.stars(),
                        counts.forks(),
                        counts.pushes(),
                        counts.issues(),
                        counts.pullRequests(),
                        counts.other(),
                        stats.uniqueActors(),
                        stats.starActors(),
                        stats.peakStars10m(),
                        stats.starsByHour().stream()
                                .map(bucket -> new HourBucketDto(bucket.hour(), bucket.stars()))
                                .toList()),
                new ScoreDto(
                        score.score(),
                        score.level().name().toLowerCase(Locale.ROOT),
                        ExplainTexts.verdict(score),
                        score.confidence(),
                        score.insufficientData(),
                        score.mocked(),
                        score.checks().stream().map(check -> toCheck(check, stats)).toList(),
                        score.rulesApplied(),
                        score.computedAt()),
                new ArchiveDto(
                        report.archive().hoursRequested(),
                        report.archive().hoursLoaded(),
                        report.archive().missingHours(),
                        report.archive().parseErrors(),
                        report.archive().cached()),
                toTimeline(report.starsTimeline()),
                new SourceDto(
                        report.source().type().name().toLowerCase(Locale.ROOT),
                        report.source().starsSampled(),
                        report.source().starsTotal(),
                        report.source().coverageFrom()));
    }

    private CheckDto toCheck(CheckContribution contribution, RepoStats stats) {
        return new CheckDto(
                contribution.checkId(),
                ExplainTexts.title(contribution.checkId()),
                ExplainTexts.summary(contribution, stats),
                contribution.score(),
                contribution.confidence(),
                contribution.weight(),
                contribution.contribution(),
                contribution.active(),
                contribution.error(),
                contribution.details());
    }

    private static StarTimelineDto toTimeline(StarTimeline timeline) {
        return new StarTimelineDto(
                timeline.bucketMinutes(),
                timeline.estimated(),
                timeline.suspiciousStars(),
                timeline.suspiciousByCheck(),
                timeline.points().stream()
                        .map(point -> new StarTimelineDto.PointDto(point.at(), point.stars(), point.suspicious()))
                        .toList());
    }
}
