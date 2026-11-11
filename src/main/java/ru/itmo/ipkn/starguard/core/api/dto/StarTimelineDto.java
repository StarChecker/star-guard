package ru.itmo.ipkn.starguard.core.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Schema(description = "Звёзды по времени: все и подозрительные (помеченные реальными проверками C2/C4)")
public record StarTimelineDto(
        @Schema(description = "Ширина точки в минутах", example = "10") @JsonProperty("bucket_minutes") int bucketMinutes,
        @Schema(description = "Подозрительные в точках оценены пропорционально внутри часа (режим индекса)")
        boolean estimated,
        @Schema(description = "Сколько звёзд окна помечено подозрительными", example = "30") @JsonProperty("suspicious_stars") int suspiciousStars,
        @Schema(description = "id проверки → число помеченных ею звёзд") @JsonProperty("suspicious_by_check") Map<String, Integer> suspiciousByCheck,
        List<PointDto> points,
        @Schema(description = "Пик C1: не более одного интервала; пусто ниже порогов. Mock/неактивный C1 даёт только нейтральный интервал")
        @JsonProperty("spike_intervals") List<SpikeIntervalDto> spikeIntervals
) {

    @Schema(description = "Полуоткрытый интервал [from, to), UTC")
    public record SpikeIntervalDto(
            Instant from, Instant to,
            @Schema(description = "Звёзды за интервал") long stars,
            @Schema(description = "Отношение к max(медиана звёзд/мин × 10, 1)") double ratio,
            @Schema(description = "Максимум звёзд за одну минуту интервала") int peak,
            @Schema(description = "Только реальный активный C1 с ненулевым score") boolean suspicious) {
    }

    @Schema(description = "Точка графика")
    public record PointDto(Instant at, int stars, int suspicious) {
    }
}
