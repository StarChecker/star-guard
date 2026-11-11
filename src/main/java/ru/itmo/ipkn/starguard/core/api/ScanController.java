package ru.itmo.ipkn.starguard.core.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.itmo.ipkn.starguard.core.api.dto.ScanDto;
import ru.itmo.ipkn.starguard.core.api.dto.ScanRequest;
import ru.itmo.ipkn.starguard.core.domain.RepoRef;
import ru.itmo.ipkn.starguard.core.scan.ScanJob;
import ru.itmo.ipkn.starguard.core.scan.ScanNotFoundException;
import ru.itmo.ipkn.starguard.core.scan.ScanService;

import java.util.Locale;

/** Скан по запросу: репозиторий вне индекса проверяется напрямую по GH Archive в фоне. */
@RestController
@RequestMapping(path = "/api/v1/scans", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "scans", description = "Скан GH Archive по запросу для репозиториев вне индекса")
public class ScanController {

    private final ScanService scanService;
    private final RepoReportMapper mapper;

    public ScanController(ScanService scanService, RepoReportMapper mapper) {
        this.scanService = scanService;
        this.mapper = mapper;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Запустить скан",
            description = "Возвращает 202 и id; повторный запрос для того же репозитория и окна вернёт тот же скан.")
    public ResponseEntity<ScanDto> start(@Valid @RequestBody ScanRequest request) {
        ScanJob.Source source = request.source() == null ? null
                : ScanJob.Source.valueOf(request.source().toUpperCase(Locale.ROOT));
        ScanJob job = scanService.start(new RepoRef(request.owner(), request.name()), request.hours(), source);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(mapper.toDto(job));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Статус скана", description = "Когда status = done, в поле report — полный отчёт.")
    public ScanDto status(@PathVariable String id) {
        return mapper.toDto(scanService.find(id).orElseThrow(() -> new ScanNotFoundException(id)));
    }
}
