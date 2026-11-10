package ru.itmo.ipkn.starguard.core.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(description = "Окно анализа (полуоткрытый интервал [from, to), UTC)")
public record WindowDto(
        Instant from,
        Instant to,
        @Schema(example = "6") int hours
) {
}
