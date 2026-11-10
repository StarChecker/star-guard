package ru.itmo.ipkn.starguard.core.index;

import ru.itmo.ipkn.starguard.core.domain.RepoRef;

import java.util.List;
import java.util.Objects;

/** Репозиторий из индекса: почасовые агрегаты в порядке возрастания времени. */
public record IndexedRepo(RepoRef repo, List<RepoHourAggregate> hours) {

    public IndexedRepo {
        Objects.requireNonNull(repo, "repo");
        hours = List.copyOf(Objects.requireNonNull(hours, "hours"));
    }
}
