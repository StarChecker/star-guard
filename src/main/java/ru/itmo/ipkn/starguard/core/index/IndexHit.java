package ru.itmo.ipkn.starguard.core.index;

import ru.itmo.ipkn.starguard.core.domain.DataSource;

import java.util.Objects;

/**
 * Результат поиска репозитория в индексе.
 *
 * @param repo почасовые агрегаты за всё окно индекса
 * @param tier топ-N или long-tail окна
 */
public record IndexHit(IndexedRepo repo, DataSource.Tier tier) {

    public IndexHit {
        Objects.requireNonNull(repo, "repo");
        Objects.requireNonNull(tier, "tier");
    }
}
