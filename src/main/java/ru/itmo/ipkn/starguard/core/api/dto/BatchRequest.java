package ru.itmo.ipkn.starguard.core.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

@Schema(description = "Пакетный запрос сводок для списков GitHub")
public record BatchRequest(
        @Schema(description = "Полные имена owner/name, до 50 штук", example = "[\"spring-projects/spring-boot\"]")
        @NotNull @Size(min = 1, max = 50)
        List<@NotNull @Pattern(regexp = "^[A-Za-z0-9_.-]{1,100}/[A-Za-z0-9_.-]{1,100}$") String> repos,
        @Schema(description = "Окно в часах; по умолчанию — из конфигурации", example = "6")
        @Min(1) @Max(24) Integer hours
) {
}
