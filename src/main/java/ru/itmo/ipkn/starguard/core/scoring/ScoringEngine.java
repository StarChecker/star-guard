package ru.itmo.ipkn.starguard.core.scoring;

import ru.itmo.ipkn.starguard.core.domain.RepoSnapshot;

/** Собирает итоговый suspicious score из результатов проверок. */
public interface ScoringEngine {

    RepoScore score(RepoSnapshot snapshot);
}
