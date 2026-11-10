package ru.itmo.ipkn.starguard.core.index;

import ru.itmo.ipkn.starguard.core.archive.GhArchiveParser;
import ru.itmo.ipkn.starguard.core.domain.StarIndex;
import ru.itmo.ipkn.starguard.core.domain.TimeWindow;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Неизменяемый снимок индекса: окно, метаданные сборки и агрегаты топ-репозиториев. */
public record RepoIndexSnapshot(
        TimeWindow window,
        Instant builtAt,
        int hoursLoaded,
        List<Instant> missingHours,
        int parseErrors,
        Map<String, IndexedRepo> repos,
        Map<Instant, StarIndex> starIndexByHour
) {

    public RepoIndexSnapshot {
        Objects.requireNonNull(window, "window");
        Objects.requireNonNull(builtAt, "builtAt");
        missingHours = List.copyOf(Objects.requireNonNull(missingHours, "missingHours"));
        repos = Map.copyOf(Objects.requireNonNull(repos, "repos"));
        starIndexByHour = starIndexByHour == null ? Map.of() : Map.copyOf(starIndexByHour);
    }

    /** Снимок без индекса звёзд: C4 в этом случае неактивна. */
    public RepoIndexSnapshot(
            TimeWindow window,
            Instant builtAt,
            int hoursLoaded,
            List<Instant> missingHours,
            int parseErrors,
            Map<String, IndexedRepo> repos) {
        this(window, builtAt, hoursLoaded, missingHours, parseErrors, repos, Map.of());
    }

    /** Индекс звёзд всех репозиториев за часы подокна (для C4). */
    public StarIndex starIndex(TimeWindow subWindow) {
        return StarIndex.merge(subWindow.hourStarts().stream()
                .map(starIndexByHour::get)
                .filter(Objects::nonNull)
                .toList());
    }

    public Optional<IndexedRepo> find(String fullName) {
        return Optional.ofNullable(repos.get(GhArchiveParser.normalizeFullName(fullName)));
    }

    /** Топ репозиториев индекса по числу звёзд за окно (для публичного списка). */
    public List<IndexedRepo> top(int limit) {
        return repos.values().stream()
                .sorted(Comparator
                        .comparingInt(RepoIndexSnapshot::totalStars)
                        .reversed()
                        .thenComparing(indexed -> indexed.repo().fullName(),
                                String.CASE_INSENSITIVE_ORDER))
                .limit(Math.max(0, limit))
                .toList();
    }

    public static int totalStars(IndexedRepo indexedRepo) {
        return indexedRepo.hours().stream()
                .mapToInt(hour -> hour.counts().stars())
                .sum();
    }

    /** Окно последних {@code hours} часов, заканчивающееся тем же {@code to}, что и индекс. */
    public TimeWindow windowFor(int hours) {
        if (hours < 1 || hours > window.hours()) {
            throw new IllegalArgumentException(
                    "hours must be within [1, %d] for the indexed window".formatted(window.hours()));
        }
        return new TimeWindow(window.to().minus(Duration.ofHours(hours)), window.to(), hours);
    }
}
