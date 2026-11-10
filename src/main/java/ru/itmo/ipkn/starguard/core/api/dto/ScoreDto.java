package ru.itmo.ipkn.starguard.core.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

@Schema(description = "Итоговый suspicious score и explain по проверкам")
public record ScoreDto(
        @Schema(example = "72") int score,
        @Schema(example = "high", allowableValues = {"low", "medium", "high", "critical"}) String level,
        @Schema(description = "Вердикт одной фразой для UI",
                example = "Заметные признаки накрутки: главный сигнал — «пересечение аккаунтов».") String verdict,
        double confidence,
        boolean insufficientData,
        @Schema(description = "true, пока C1–C5 — моки (PoC)", example = "true") boolean mocked,
        List<CheckDto> checks,
        List<String> rulesApplied,
        Instant computedAt
) {
}
