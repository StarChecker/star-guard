package ru.itmo.ipkn.starguard.core.github;

import ru.itmo.ipkn.starguard.core.domain.RepoRef;

import java.time.Instant;
import java.util.Objects;

/**
 * Базовая статистика репозитория из GitHub REST — без score и без данных GH Archive.
 *
 * @param contributors число контрибьюторов (с анонимными); null — GitHub не отдал (слишком много или ошибка)
 * @param cached       ответ взят из кэша
 */
public record RepoPreview(
        RepoRef repo,
        String description,
        String htmlUrl,
        int stars,
        int forks,
        int openIssues,
        Integer watchers,
        Integer contributors,
        boolean archived,
        boolean fork,
        Instant createdAt,
        Instant pushedAt,
        Instant fetchedAt,
        boolean cached
) {

    public RepoPreview {
        Objects.requireNonNull(repo, "repo");
        Objects.requireNonNull(fetchedAt, "fetchedAt");
    }

    RepoPreview fromCache() {
        return new RepoPreview(repo, description, htmlUrl, stars, forks, openIssues, watchers, contributors,
                archived, fork, createdAt, pushedAt, fetchedAt, true);
    }
}
