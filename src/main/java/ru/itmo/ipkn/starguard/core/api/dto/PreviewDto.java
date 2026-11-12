package ru.itmo.ipkn.starguard.core.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(description = """
        Базовая статистика репозитория из GitHub REST — без score и без данных GH Archive.
        Для холодных репозиториев: показать хоть что-то, пока полный анализ идёт в фоне.
        """)
public record PreviewDto(
        RepoDto repo,
        @Schema(description = "Источник данных", example = "github") String source,
        @Schema(description = "Всегда true: это не полный отчёт, score здесь нет", example = "true") boolean partial,
        @Schema(description = "Пометка для UI", example = "Нет данных GH Archive: базовая статистика GitHub, без оценки.")
        String note,
        String description,
        @Schema(example = "https://github.com/octocat/hello-world") String htmlUrl,
        @Schema(description = "Звёзд всего", example = "2650") int stars,
        @Schema(description = "Форков всего", example = "2400") int forks,
        @Schema(description = "Открытых issues и PR", example = "120") int openIssues,
        @Schema(description = "Наблюдателей (watchers); null — GitHub не отдал") Integer watchers,
        @Schema(description = "Контрибьюторов с анонимными; null — GitHub не отдал (очень большой репозиторий или лимит)")
        Integer contributors,
        boolean archived,
        @Schema(description = "Репозиторий сам является форком") boolean fork,
        Instant createdAt,
        @Schema(description = "Последний push") Instant pushedAt,
        Instant fetchedAt,
        @Schema(description = "Ответ из кэша сервиса") boolean cached
) {
}
