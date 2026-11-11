package ru.itmo.ipkn.starguard.core.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

@Schema(description = "Аккаунт, отмеченный реальной активной C2/C4 с ненулевым score; это признак, не доказательство")
public record SuspiciousActorDto(
        @Schema(description = "Стабильный SHA-256 hash actorId (12 hex-символов)") @JsonProperty("actor_hash") String actorHash,
        @Schema(description = "GitHub login; null в hash-режиме или без сохранённого профиля", nullable = true) String login,
        @Schema(description = "Причины: fresh — C2, overlap — C4") List<String> reasons,
        @Schema(description = "Дата создания аккаунта, если известна", nullable = true) @JsonProperty("account_created_at") Instant accountCreatedAt,
        @Schema(description = "Первое событие аккаунта в репозитории за окно; без профиля — первая известная звезда (в индексе до часа)", nullable = true) @JsonProperty("first_seen") Instant firstSeen,
        @Schema(description = "Число других репозиториев индекса окна, которым аккаунт поставил звезду; при неполном индексе — нижняя граница") @JsonProperty("shared_repos") int sharedRepos
) {
}
