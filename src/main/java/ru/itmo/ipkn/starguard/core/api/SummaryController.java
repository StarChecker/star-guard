package ru.itmo.ipkn.starguard.core.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.itmo.ipkn.starguard.core.api.dto.BadgeDto;
import ru.itmo.ipkn.starguard.core.api.dto.BatchRequest;
import ru.itmo.ipkn.starguard.core.api.dto.RepoSummaryDto;
import ru.itmo.ipkn.starguard.core.domain.RepoRef;
import ru.itmo.ipkn.starguard.core.scoring.RepoScore;
import ru.itmo.ipkn.starguard.core.service.RepoReport;
import ru.itmo.ipkn.starguard.core.service.RepoSummary;
import ru.itmo.ipkn.starguard.core.service.RepoSummaryService;

import java.util.List;
import java.util.Locale;

/**
 * Дешёвые сводки: пакетно для списков GitHub в расширении и бейдж для README.
 * Берут данные только из индекса (или фикстур), прямой скан не запускают.
 */
@Validated
@RestController
@Tag(name = "summary", description = "Сводки для списков GitHub и бейдж shields.io")
public class SummaryController {

    private static final String NAME_PATTERN = "^[A-Za-z0-9_.-]{1,100}$";
    private static final int BADGE_CACHE_SECONDS = 1800;

    private final RepoSummaryService summaryService;

    public SummaryController(RepoSummaryService summaryService) {
        this.summaryService = summaryService;
    }

    @PostMapping(path = "/api/v1/repos/batch",
            consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Сводки по списку репозиториев",
            description = "До 50 репозиториев за запрос. Репозитории вне индекса получают status = not_indexed.")
    public List<RepoSummaryDto> batch(@Valid @RequestBody BatchRequest request) {
        List<RepoRef> repos = request.repos().stream().map(SummaryController::parse).toList();
        return summaryService.summarize(repos, request.hours()).stream()
                .map(SummaryController::toDto)
                .toList();
    }

    @GetMapping(path = "/api/v1/badge/{owner}/{name}", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Бейдж для README (shields.io endpoint)",
            description = """
                    ![Star Guard](https://img.shields.io/endpoint?url=https://<host>/api/v1/badge/<owner>/<name>)
                    """)
    public BadgeDto badge(
            @PathVariable @Pattern(regexp = NAME_PATTERN) String owner,
            @PathVariable @Pattern(regexp = NAME_PATTERN) String name,
            @Parameter(description = "Окно в часах") @RequestParam(name = "hours", required = false)
            @Min(1) @Max(24) Integer hours) {
        RepoSummary summary = summaryService.summarize(new RepoRef(owner, name), hours);
        return toBadge(summary);
    }

    static BadgeDto toBadge(RepoSummary summary) {
        if (summary.status() != RepoSummary.Status.OK) {
            String message = switch (summary.status()) {
                case NOT_INDEXED -> "not tracked";
                case NO_DATA -> "no recent stars";
                case INDEX_NOT_READY -> "warming up";
                default -> "unavailable";
            };
            return new BadgeDto(1, "Star Guard", message, "lightgrey", BADGE_CACHE_SECONDS);
        }
        RepoScore score = summary.report().score();
        if (score.insufficientData()) {
            return new BadgeDto(1, "Star Guard", "not enough data", "lightgrey", BADGE_CACHE_SECONDS);
        }
        String text = switch (score.level()) {
            case LOW -> "no signs of fake stars";
            case MEDIUM -> "minor signals";
            case HIGH -> "suspicious stars";
            case CRITICAL -> "highly suspicious stars";
        };
        String color = switch (score.level()) {
            case LOW -> "2ea44f";
            case MEDIUM -> "d4a72c";
            case HIGH -> "e16f24";
            case CRITICAL -> "cf222e";
        };
        return new BadgeDto(1, "Star Guard", text + " · " + score.score() + "/100", color, BADGE_CACHE_SECONDS);
    }

    static RepoSummaryDto toDto(RepoSummary summary) {
        String status = summary.status().name().toLowerCase(Locale.ROOT);
        RepoReport report = summary.report();
        if (report == null) {
            return new RepoSummaryDto(summary.repo().fullName(), status,
                    null, null, null, null, null, null, false);
        }
        RepoScore score = report.score();
        return new RepoSummaryDto(
                summary.repo().fullName(),
                status,
                score.score(),
                score.level().name().toLowerCase(Locale.ROOT),
                score.confidence(),
                ExplainTexts.verdict(score),
                report.stats().counts().stars(),
                report.starsTimeline().suspiciousStars(),
                score.mocked());
    }

    private static RepoRef parse(String fullName) {
        int slash = fullName.indexOf('/');
        return new RepoRef(fullName.substring(0, slash), fullName.substring(slash + 1));
    }
}
