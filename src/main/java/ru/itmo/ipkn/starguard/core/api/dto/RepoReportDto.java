package ru.itmo.ipkn.starguard.core.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Отчёт по репозиторию. В PoC проверки C1–C5 замоканы (score.mocked = true)")
public record RepoReportDto(
        RepoDto repo,
        WindowDto window,
        StatsDto stats,
        ScoreDto score,
        ArchiveDto archive
) {
}
