package ru.itmo.ipkn.starguard.core.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Pattern;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.itmo.ipkn.starguard.core.api.dto.TrackedRepoDto;
import ru.itmo.ipkn.starguard.core.api.dto.TrackerStatusDto;
import ru.itmo.ipkn.starguard.core.config.TrackerProperties;
import ru.itmo.ipkn.starguard.core.domain.RepoRef;
import ru.itmo.ipkn.starguard.core.github.RepoGoneException;
import ru.itmo.ipkn.starguard.core.tracker.RepoNotTrackedException;
import ru.itmo.ipkn.starguard.core.tracker.StarTracker;
import ru.itmo.ipkn.starguard.core.tracker.TrackerStore;

import java.time.Duration;
import java.util.List;

@RestController
@Validated
@ConditionalOnProperty(name = TrackerProperties.ENABLED_PROPERTY, havingValue = "true")
@RequestMapping(path = "/api/v1/tracker", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "tracker", description = "Снимки звёзд каждые 5 минут: топ по звёздам и проверенные репозитории")
public class TrackerController {

    private static final String NAME_PATTERN = "^[A-Za-z0-9_.-]{1,100}$";

    private final StarTracker tracker;

    public TrackerController(StarTracker tracker) {
        this.tracker = tracker;
    }

    @GetMapping("/status")
    @Operation(summary = "Состояние трекера: сколько репозиториев отслеживается и как прошёл последний опрос")
    public TrackerStatusDto status() {
        StarTracker.Status status = tracker.status();
        TrackerStore.PollRun run = status.lastRun();
        return new TrackerStatusDto(
                status.active(),
                status.popular(),
                status.watched(),
                TrackerStatusDto.seconds(status.pollInterval()),
                run == null ? null : run.at(),
                run == null ? null : run.repos(),
                run == null ? null : run.failed(),
                run == null ? null : run.durationMs(),
                status.rateLimitRemaining());
    }

    @GetMapping("/repos/{owner}/{name}")
    @Operation(summary = "Снимки звёзд репозитория: за 24 часа с шагом опроса и по часам за неделю")
    public TrackedRepoDto repo(
            @PathVariable @Pattern(regexp = NAME_PATTERN) String owner,
            @PathVariable @Pattern(regexp = NAME_PATTERN) String name) {
        RepoRef repo = new RepoRef(owner, name);
        return toDto(tracker.find(repo).orElseThrow(() -> new RepoNotTrackedException(repo)));
    }

    @PutMapping("/repos/{owner}/{name}")
    @Operation(summary = "Поставить репозиторий на наблюдение (продлевает срок, если уже отслеживается)")
    public TrackedRepoDto watch(
            @PathVariable @Pattern(regexp = NAME_PATTERN) String owner,
            @PathVariable @Pattern(regexp = NAME_PATTERN) String name) {
        RepoRef repo = new RepoRef(owner, name);
        return toDto(tracker.watch(repo).orElseThrow(() -> new RepoGoneException(repo)));
    }

    private TrackedRepoDto toDto(TrackerStore.TrackedRepo repo) {
        return new TrackedRepoDto(
                repo.fullName(),
                repo.popularRank(),
                repo.watchedUntil(),
                repo.trackedSince(),
                repo.lastPolledAt(),
                repo.stars(),
                repo.forks(),
                points(tracker.rawSeries(repo, Duration.ofDays(1))),
                points(tracker.hourlySeries(repo, Duration.ofDays(7))));
    }

    private static List<TrackedRepoDto.Point> points(List<TrackerStore.Point> points) {
        return points.stream().map(p -> new TrackedRepoDto.Point(p.at(), p.stars(), p.forks())).toList();
    }
}
