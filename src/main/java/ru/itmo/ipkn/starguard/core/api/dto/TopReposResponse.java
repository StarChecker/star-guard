package ru.itmo.ipkn.starguard.core.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

@Schema(description = "Предвычисленный топ репозиториев, доступных для запросов")
public record TopReposResponse(
        WindowDto window,
        Instant builtAt,
        @Schema(description = "Сколько репозиториев в индексе", example = "1000") int topN,
        int hoursLoaded,
        List<Instant> missingHours,
        int parseErrors,
        List<TopRepoDto> repos
) {

    @Schema(description = "Одна строка топа")
    public record TopRepoDto(
            @Schema(example = "1") int rank,
            @Schema(example = "spring-projects") String owner,
            @Schema(example = "spring-boot") String name,
            @Schema(example = "spring-projects/spring-boot") String fullName,
            @Schema(description = "Звёзды за индексированное окно", example = "142") int stars
    ) {
    }
}
