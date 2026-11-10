package ru.itmo.ipkn.starguard.core.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Schema(description = "Звёзды по времени: все и подозрительные (помеченные реальными проверками C2/C4)")
public record StarTimelineDto(
        @Schema(description = "Ширина точки в минутах", example = "10") int bucketMinutes,
        @Schema(description = "Подозрительные в точках оценены пропорционально внутри часа (режим индекса)")
        boolean estimated,
        @Schema(description = "Сколько звёзд окна помечено подозрительными", example = "30") int suspiciousStars,
        @Schema(description = "id проверки → число помеченных ею звёзд") Map<String, Integer> suspiciousByCheck,
        List<PointDto> points
) {

    @Schema(description = "Точка графика")
    public record PointDto(Instant at, int stars, int suspicious) {
    }
}
