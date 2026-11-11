package ru.itmo.ipkn.starguard.core.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

@Schema(description = "Добавить (закрепить) репозиторий в индексе")
public record IndexRepoRequest(
        @Schema(example = "spring-projects") @NotNull @Pattern(regexp = "^[A-Za-z0-9_.-]{1,100}$") String owner,
        @Schema(example = "spring-boot") @NotNull @Pattern(regexp = "^[A-Za-z0-9_.-]{1,100}$") String name,
        @Schema(description = "Окно отчёта в часах (для 200) или скана (для 202); по умолчанию — из конфигурации",
                example = "6")
        @Min(1) @Max(24) Integer hours
) {
}
