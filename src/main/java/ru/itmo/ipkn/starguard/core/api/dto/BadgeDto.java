package ru.itmo.ipkn.starguard.core.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

/** Формат shields.io endpoint badge: https://shields.io/badges/endpoint-badge */
@Schema(description = "JSON для shields.io endpoint badge")
public record BadgeDto(
        @JsonProperty("schemaVersion") int schemaVersion,
        @JsonProperty("label") String label,
        @JsonProperty("message") String message,
        @JsonProperty("color") String color,
        @JsonProperty("cacheSeconds") int cacheSeconds
) {
}
