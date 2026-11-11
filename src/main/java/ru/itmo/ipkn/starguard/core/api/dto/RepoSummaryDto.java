package ru.itmo.ipkn.starguard.core.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Короткая сводка по репозиторию")
public record RepoSummaryDto(
        @Schema(example = "spring-projects/spring-boot") String fullName,
        @Schema(example = "ok", allowableValues = {"ok", "not_indexed", "no_data", "index_not_ready", "error"})
        String status,
        @Schema(description = "null, если status != ok", example = "12") Integer score,
        @Schema(example = "low") String level,
        Double confidence,
        @Schema(description = "Вердикт одной фразой") String verdict,
        @Schema(description = "Звёзды за окно") Integer stars,
        @Schema(description = "Из них помечено подозрительными") Integer suspiciousStars,
        boolean mocked
) {
}
