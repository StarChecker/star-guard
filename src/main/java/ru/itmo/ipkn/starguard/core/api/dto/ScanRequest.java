package ru.itmo.ipkn.starguard.core.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

@Schema(description = "Запуск скана GH Archive для репозитория вне индекса")
public record ScanRequest(
        @Schema(example = "spring-projects") @NotNull @Pattern(regexp = "^[A-Za-z0-9_.-]{1,100}$") String owner,
        @Schema(example = "spring-boot") @NotNull @Pattern(regexp = "^[A-Za-z0-9_.-]{1,100}$") String name,
        @Schema(example = "6") @Min(1) @Max(24) Integer hours
) {
}
