package ru.itmo.ipkn.starguard.core.index;

import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Выбирает топ-N репозиториев по числу звёзд за окно.
 * Тай-брейк — по нормализованному полному имени, чтобы результаты были детерминированы.
 */
public final class TopRepoSelector {

    private TopRepoSelector() {
    }

    public static Set<String> select(Map<String, Integer> starsByRepo, int topN) {
        if (topN < 1) {
            throw new IllegalArgumentException("topN must be >= 1");
        }
        return starsByRepo.entrySet().stream()
                .filter(entry -> entry.getValue() != null && entry.getValue() > 0)
                .sorted(Comparator
                        .comparingInt((Map.Entry<String, Integer> entry) -> entry.getValue())
                        .reversed()
                        .thenComparing(Map.Entry::getKey))
                .limit(topN)
                .map(Map.Entry::getKey)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }
}
