package ru.itmo.ipkn.starguard.core.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(description = "Звёзды за один час окна")
public record HourBucketDto(
        Instant hour,
        int stars
) {
}
