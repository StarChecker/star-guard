package ru.itmo.ipkn.starguard.core.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

@Schema(description = "Сведения о загрузке окна из GH Archive")
public record ArchiveDto(
        int hoursRequested,
        int hoursLoaded,
        @Schema(description = "Часы, файлы которых отсутствуют или не загрузились") List<Instant> missingHours,
        @Schema(description = "Строки, которые не удалось разобрать") int parseErrors,
        boolean cached
) {
}
