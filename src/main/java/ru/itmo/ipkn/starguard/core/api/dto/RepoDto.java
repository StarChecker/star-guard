package ru.itmo.ipkn.starguard.core.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Идентификация репозитория")
public record RepoDto(
        @Schema(example = "spring-projects") String owner,
        @Schema(example = "spring-boot") String name,
        @Schema(example = "spring-projects/spring-boot") String fullName
) {
}
