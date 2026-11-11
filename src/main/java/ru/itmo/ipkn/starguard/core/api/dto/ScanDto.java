package ru.itmo.ipkn.starguard.core.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(description = "Скан GH Archive по запросу для репозитория вне индекса")
public record ScanDto(
        String id,
        RepoDto repo,
        @Schema(allowableValues = {"queued", "running", "done", "failed"}) String status,
        @Schema(allowableValues = {"github", "archive"}) String source,
        @Schema(description = "Сколько шагов выполнено: часовых дампов или страниц истории GitHub") int hoursDone,
        @Schema(description = "Всего шагов") int hoursTotal,
        Instant createdAt,
        @Schema(description = "Отчёт, когда status = done") RepoReportDto report,
        @Schema(description = "Код ошибки, когда status = failed", example = "REPO_NOT_FOUND_IN_WINDOW")
        String errorCode,
        String errorMessage
) {
}
