package ru.itmo.ipkn.starguard.core.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Map;

@Schema(description = "Результат одной проверки и её вклад в итоговый score")
public record CheckDto(
        @Schema(example = "burst") String id,
        @Schema(description = "Название проверки для UI", example = "Всплески звёзд") String title,
        @Schema(description = "Короткая причина человеческим языком",
                example = "Пик — 31 звезда за 10 минут.") String summary,
        double score,
        double confidence,
        double weight,
        double contribution,
        boolean active,
        String error,
        Map<String, Object> details
) {
}
