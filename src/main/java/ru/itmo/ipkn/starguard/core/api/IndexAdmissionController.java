package ru.itmo.ipkn.starguard.core.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.itmo.ipkn.starguard.core.api.dto.IndexRepoRequest;
import ru.itmo.ipkn.starguard.core.api.dto.RepoReportDto;
import ru.itmo.ipkn.starguard.core.api.dto.ScanDto;
import ru.itmo.ipkn.starguard.core.config.ArchiveProperties;
import ru.itmo.ipkn.starguard.core.domain.RepoRef;
import ru.itmo.ipkn.starguard.core.index.IndexAdmission;
import ru.itmo.ipkn.starguard.core.index.IndexNotReadyException;
import ru.itmo.ipkn.starguard.core.scan.ScanJob;
import ru.itmo.ipkn.starguard.core.scan.ScanService;
import ru.itmo.ipkn.starguard.core.service.RepoReportService;

import java.net.URI;

/**
 * Добавление репозитория в индекс (F4-2). Идемпотентно: повторный запрос закрепляет
 * тот же репозиторий и возвращает тот же результат (отчёт или тот же скан).
 */
@Validated
@RestController
@RequestMapping(path = "/api/v1/index", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "index", description = "Репозитории, которые можно запрашивать у сервиса")
public class IndexAdmissionController {

    private static final String NAME_PATTERN = "^[A-Za-z0-9_.-]{1,100}$";
    private static final String DESCRIPTION = """
            Закрепляет репозиторий в топе индекса: он остаётся там при пересборках, пока
            у него есть события в окне. Повторный запрос ничего не меняет.

            Если у репозитория есть события в окне индекса (он в топе или в long-tail),
            он сразу переносится в топ и ответ — 200 с отчётом (source.tier = top).
            Если событий в окне нет — 202, запускается фоновый скан (история GitHub при
            наличии токена, иначе GH Archive за max-window-hours); статус — в Location
            (GET /api/v1/scans/{id}), повторный запрос вернёт тот же скан.
            """;

    private final ObjectProvider<IndexAdmission> admissionProvider;
    private final RepoReportService reportService;
    private final ScanService scanService;
    private final ArchiveProperties archiveProperties;
    private final RepoReportMapper mapper;

    public IndexAdmissionController(
            ObjectProvider<IndexAdmission> admissionProvider,
            RepoReportService reportService,
            ScanService scanService,
            ArchiveProperties archiveProperties,
            RepoReportMapper mapper) {
        this.admissionProvider = admissionProvider;
        this.reportService = reportService;
        this.scanService = scanService;
        this.archiveProperties = archiveProperties;
        this.mapper = mapper;
    }

    @PutMapping("/repos/{owner}/{name}")
    @Operation(summary = "Добавить или закрепить репозиторий в индексе", description = DESCRIPTION)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Репозиторий в топе индекса — отчёт",
                    content = @Content(schema = @Schema(implementation = RepoReportDto.class))),
            @ApiResponse(responseCode = "202", description = "Событий в окне нет — запущен фоновый скан",
                    content = @Content(schema = @Schema(implementation = ScanDto.class))),
            @ApiResponse(responseCode = "400", description = "Некорректные параметры"),
            @ApiResponse(responseCode = "429", description = "PIN_LIMIT_REACHED — реестр закреплённых заполнен"),
            @ApiResponse(responseCode = "503", description = "Индекс отключён или ещё строится")
    })
    public ResponseEntity<?> pin(
            @Parameter(description = "Владелец репозитория", example = "spring-projects")
            @PathVariable @Pattern(regexp = NAME_PATTERN) String owner,
            @Parameter(description = "Имя репозитория", example = "spring-boot")
            @PathVariable @Pattern(regexp = NAME_PATTERN) String name,
            @Parameter(description = "Окно отчёта в часах; по умолчанию — из конфигурации", example = "6")
            @RequestParam(name = "hours", required = false) @Min(1) @Max(24) Integer hours) {
        return admit(new RepoRef(owner, name), hours);
    }

    @PostMapping(path = "/repos", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Добавить репозиторий в индекс", description = DESCRIPTION)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Репозиторий в топе индекса — отчёт",
                    content = @Content(schema = @Schema(implementation = RepoReportDto.class))),
            @ApiResponse(responseCode = "202", description = "Событий в окне нет — запущен фоновый скан",
                    content = @Content(schema = @Schema(implementation = ScanDto.class))),
            @ApiResponse(responseCode = "400", description = "Некорректные параметры"),
            @ApiResponse(responseCode = "429", description = "PIN_LIMIT_REACHED — реестр закреплённых заполнен"),
            @ApiResponse(responseCode = "503", description = "Индекс отключён или ещё строится")
    })
    public ResponseEntity<?> add(@Valid @RequestBody IndexRepoRequest request) {
        return admit(new RepoRef(request.owner(), request.name()), request.hours());
    }

    private ResponseEntity<?> admit(RepoRef repo, Integer hours) {
        IndexAdmission admission = admissionProvider.getIfAvailable();
        if (admission == null) {
            throw new IndexNotReadyException(
                    "Repository index is disabled (fixture mode or app.index.enabled=false)");
        }
        if (admission.admit(repo) == IndexAdmission.Outcome.INDEXED) {
            return ResponseEntity.ok(mapper.toDto(reportService.getReport(repo, hours, null)));
        }
        // Событий в окне индекса нет: сканируем шире — GH Archive за max-window-hours.
        ScanJob job = scanService.start(repo, hours == null ? archiveProperties.maxWindowHours() : hours);
        return ResponseEntity.accepted()
                .location(URI.create("/api/v1/scans/" + job.id()))
                .body(mapper.toDto(job));
    }
}
