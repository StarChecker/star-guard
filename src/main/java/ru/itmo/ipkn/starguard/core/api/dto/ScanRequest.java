package ru.itmo.ipkn.starguard.core.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.time.LocalDate;

@Schema(description = "Запуск скана для репозитория вне индекса")
public record ScanRequest(
        @Schema(example = "spring-projects") @NotNull @Pattern(regexp = "^[A-Za-z0-9_.-]{1,100}$") String owner,
        @Schema(example = "spring-boot") @NotNull @Pattern(regexp = "^[A-Za-z0-9_.-]{1,100}$") String name,
        @Schema(example = "6") @Min(1) @Max(24) Integer hours,
        @Schema(description = "История GitHub: окно в днях (1–3650)", example = "30")
        @Min(1) @Max(3650) Integer days,
        @Schema(description = "История GitHub: дата окончания периода включительно (ISO yyyy-MM-dd)")
        LocalDate to,
        @Schema(description = "История GitHub: вся история репозитория")
        Boolean all,
        @Schema(description = "github — история GitHub (нужен токен), archive — дампы GH Archive; по умолчанию выбирается сам",
                allowableValues = {"github", "archive"})
        @Pattern(regexp = "^(github|archive)$") String source
) {
}
