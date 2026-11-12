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
        @Schema(description = "Сколько строк топа пропущено: репозиторий удалён или скрыт на GitHub")
        int skippedDeleted,
        @Schema(description = "Сколько строк пропущено фильтром max_level")
        int skippedByLevel,
        List<TopRepoDto> repos
) {

    @Schema(description = "Одна строка топа")
    public record TopRepoDto(
            @Schema(description = "Место в индексе по звёздам за окно", example = "1") int rank,
            @Schema(example = "spring-projects") String owner,
            @Schema(example = "spring-boot") String name,
            @Schema(example = "spring-projects/spring-boot") String fullName,
            @Schema(description = "Звёзды за индексированное окно", example = "142") int stars,
            @Schema(description = "Есть на GitHub: true, удалён: false, не проверялось: null") Boolean exists,
            @Schema(description = "Оценка 0–100, если запрошено with_score") Integer score,
            @Schema(description = "Уровень, если запрошено with_score") String level,
            @Schema(description = "Вердикт, если запрошено with_score") String verdict
    ) {
    }
}
