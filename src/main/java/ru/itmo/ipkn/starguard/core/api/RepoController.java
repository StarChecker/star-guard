package ru.itmo.ipkn.starguard.core.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.itmo.ipkn.starguard.core.api.dto.RepoReportDto;
import ru.itmo.ipkn.starguard.core.archive.ArchiveProgress;
import ru.itmo.ipkn.starguard.core.domain.RepoRef;
import ru.itmo.ipkn.starguard.core.scoring.MockMode;
import ru.itmo.ipkn.starguard.core.service.RepoReportService;

@Validated
@RestController
@RequestMapping(path = "/api/v1/repos", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "repos", description = "Статистика репозитория и suspicious score (PoC, проверки замоканы)")
public class RepoController {

    private static final String NAME_PATTERN = "^[A-Za-z0-9_.-]{1,100}$";

    private final RepoReportService reportService;
    private final RepoReportMapper mapper;

    public RepoController(RepoReportService reportService, RepoReportMapper mapper) {
        this.reportService = reportService;
        this.mapper = mapper;
    }

    @GetMapping("/{owner}/{name}")
    @Operation(
            summary = "Статистика репозитория и suspicious score",
            description = """
                    Источник выбирает app.report.source. По умолчанию (auto) при наличии GITHUB_TOKEN
                    отчёт строится по истории GitHub за days дней: дневная история звёзд, активность
                    и снимки трекера. Без токена - по индексу GH Archive за hours часов.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Отчёт по репозиторию"),
            @ApiResponse(responseCode = "400", description = "Некорректные параметры запроса"),
            @ApiResponse(responseCode = "404", description = "В окне нет событий репозитория"),
            @ApiResponse(responseCode = "502", description = "GH Archive недоступен для всего окна"),
            @ApiResponse(responseCode = "500", description = "Внутренняя ошибка")
    })
    public RepoReportDto getRepoReport(
            @Parameter(description = "Владелец репозитория", example = "spring-projects")
            @PathVariable @Pattern(regexp = NAME_PATTERN) String owner,
            @Parameter(description = "Имя репозитория", example = "spring-boot")
            @PathVariable @Pattern(regexp = NAME_PATTERN) String name,
            @Parameter(description = "Размер окна в часах; по умолчанию — из конфигурации", example = "6")
            @RequestParam(name = "hours", required = false) @Min(1) @Max(24) Integer hours,
            @Parameter(description = "Окно анализа в днях для истории GitHub; по умолчанию app.github.history.max-days")
            @RequestParam(name = "days", required = false) @Min(1) @Max(90) Integer days,
            @Parameter(description = "PoC: профиль мок-проверок (healthy | borderline | suspicious)")
            @RequestParam(name = "mock_mode", required = false) String mockMode) {

        MockMode requestedMockMode = MockMode.fromNullable(mockMode);
        return mapper.toDto(reportService.getReport(
                new RepoRef(owner, name), hours, days, requestedMockMode));
    }

    @GetMapping("/{owner}/{name}/history")
    @Operation(
            summary = "Анализ любого репозитория по истории GitHub",
            description = """
                    Дневная история звёзд (GET /repos/{owner}/{repo}/stargazers/history), форки, issues,
                    PR и коммиты за days дней (по умолчанию app.github.history.max-days) и, если репозиторий
                    отслеживается трекером, снимки звёзд каждые 5 минут. Работает для любого публичного
                    репозитория, нужен GITHUB_TOKEN. Кто поставил звёзды, GitHub не отдаёт, поэтому
                    проверок по аккаунтам в этом режиме нет.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Отчёт по репозиторию"),
            @ApiResponse(responseCode = "400", description = "Некорректные параметры запроса"),
            @ApiResponse(responseCode = "404", description = "Репозиторий удалён или приватный"),
            @ApiResponse(responseCode = "503", description = "Нет токена GitHub или GitHub API недоступен")
    })
    public RepoReportDto getRepoHistory(
            @PathVariable @Pattern(regexp = NAME_PATTERN) String owner,
            @PathVariable @Pattern(regexp = NAME_PATTERN) String name,
            @Parameter(description = "Окно анализа в днях; по умолчанию app.github.history.max-days")
            @RequestParam(name = "days", required = false) @Min(1) @Max(90) Integer days,
            @Parameter(description = "PoC: профиль мок-проверок (healthy | borderline | suspicious)")
            @RequestParam(name = "mock_mode", required = false) String mockMode) {
        return mapper.toDto(reportService.historyReport(
                new RepoRef(owner, name), days, MockMode.fromNullable(mockMode), ArchiveProgress.NONE));
    }
}
