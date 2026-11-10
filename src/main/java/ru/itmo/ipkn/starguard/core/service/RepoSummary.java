package ru.itmo.ipkn.starguard.core.service;

import ru.itmo.ipkn.starguard.core.domain.RepoRef;

import java.util.Objects;

/**
 * Короткая сводка по репозиторию для списков GitHub и бейджа.
 *
 * @param status ok | not_indexed | no_data | index_not_ready | error
 * @param report полный отчёт, если {@code status = ok}
 */
public record RepoSummary(RepoRef repo, Status status, RepoReport report) {

    public enum Status { OK, NOT_INDEXED, NO_DATA, INDEX_NOT_READY, ERROR }

    public RepoSummary {
        Objects.requireNonNull(repo, "repo");
        Objects.requireNonNull(status, "status");
    }
}
