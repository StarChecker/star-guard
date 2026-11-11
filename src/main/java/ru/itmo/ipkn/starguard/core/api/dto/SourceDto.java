package ru.itmo.ipkn.starguard.core.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(description = "Источник данных отчёта")
public record SourceDto(
        @Schema(allowableValues = {"gh_archive_index", "gh_archive", "github_api"}) String type,
        @Schema(description = "Сколько последних звёзд загружено (история GitHub)") Integer starsSampled,
        @Schema(description = "Сколько звёзд у репозитория всего (история GitHub)") Integer starsTotal,
        @Schema(description = "С какого момента история полная (история GitHub)") Instant coverageFrom,
        @Schema(description = "Уровень индекса для gh_archive_index: top — топ-N по звёздам, "
                + "long_tail — остальные репозитории с событиями в окне; null для других источников",
                allowableValues = {"top", "long_tail"}) String tier
) {
}
