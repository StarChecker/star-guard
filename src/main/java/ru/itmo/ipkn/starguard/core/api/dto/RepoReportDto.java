package ru.itmo.ipkn.starguard.core.api.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Отчёт по репозиторию; доступность реальных проверок указана в score.checks")
public record RepoReportDto(
        RepoDto repo,
        WindowDto window,
        StatsDto stats,
        ScoreDto score,
        ArchiveDto archive,
        @Schema(description = "Звёзды по времени с разметкой подозрительных — для графика")
        @JsonProperty("stars_timeline") StarTimelineDto starsTimeline,
        @Schema(description = "Откуда данные: индекс GH Archive, прямой скан GH Archive или история GitHub")
        SourceDto source,
        @Schema(description = "Выборка по числу причин, затем shared_repos по убыванию, затем actor_hash; ограничена app.api.suspicious-actors-limit")
        @JsonProperty("suspicious_actors") List<SuspiciousActorDto> suspiciousActors,
        @Schema(description = "Всего уникальных отмеченных аккаунтов до лимита выборки") @JsonProperty("suspicious_actors_total") int suspiciousActorsTotal
) {
}
