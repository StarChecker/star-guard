package ru.itmo.ipkn.starguard.core.github;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record StarHistoryWeek(
        @JsonProperty("week") long week,
        @JsonProperty("total") int total,
        @JsonProperty("days") List<Integer> days
) {
}
