package ru.itmo.ipkn.starguard.core.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "Статистика репозитория за окно, посчитанная по событиям GH Archive")
public record StatsDto(
        @Schema(example = "4231") int totalEvents,
        @Schema(example = "142") int stars,
        @Schema(example = "21") int forks,
        @Schema(example = "3110") int pushes,
        @Schema(example = "233") int issues,
        @Schema(example = "725") int pullRequests,
        int other,
        @Schema(example = "611") int uniqueActors,
        @Schema(example = "138") int starActors,
        @JsonProperty("peak_stars_10m")
        @Schema(description = "Максимум звёзд в любом 10-минутном окне", example = "9") int peakStars10m,
        List<HourBucketDto> starsByHour
) {
}
