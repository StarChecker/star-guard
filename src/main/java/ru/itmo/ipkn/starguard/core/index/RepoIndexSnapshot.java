package ru.itmo.ipkn.starguard.core.index;

import ru.itmo.ipkn.starguard.core.archive.GhArchiveParser;
import ru.itmo.ipkn.starguard.core.domain.DataSource;
import ru.itmo.ipkn.starguard.core.domain.RepoRef;
import ru.itmo.ipkn.starguard.core.domain.StarIndex;
import ru.itmo.ipkn.starguard.core.domain.TimeWindow;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Неизменяемый снимок индекса: окно, метаданные сборки, агрегаты топ-репозиториев и
 * дайджесты часов окна — из них отдаются остальные репозитории окна (long-tail, F4-1).
 *
 * @param repos   топ-N по звёздам за окно (нормализованное имя → агрегаты)
 * @param digests дайджесты часов окна: агрегаты всех репозиториев, у которых были события;
 *                те же объекты, что держит сборщик, — отдельной памяти не занимают
 */
public record RepoIndexSnapshot(
        TimeWindow window,
        Instant builtAt,
        int hoursLoaded,
        List<Instant> missingHours,
        int parseErrors,
        Map<String, IndexedRepo> repos,
        Map<Instant, StarIndex> starIndexByHour,
        Map<Instant, HourDigest> digests
) {

    public RepoIndexSnapshot {
        Objects.requireNonNull(window, "window");
        Objects.requireNonNull(builtAt, "builtAt");
        missingHours = List.copyOf(Objects.requireNonNull(missingHours, "missingHours"));
        repos = Map.copyOf(Objects.requireNonNull(repos, "repos"));
        starIndexByHour = starIndexByHour == null ? Map.of() : Map.copyOf(starIndexByHour);
        digests = digests == null ? Map.of() : Map.copyOf(digests);
    }

    /** Снимок без long-tail: отвечает только по топ-N. */
    public RepoIndexSnapshot(
            TimeWindow window,
            Instant builtAt,
            int hoursLoaded,
            List<Instant> missingHours,
            int parseErrors,
            Map<String, IndexedRepo> repos,
            Map<Instant, StarIndex> starIndexByHour) {
        this(window, builtAt, hoursLoaded, missingHours, parseErrors, repos, starIndexByHour, Map.of());
    }

    /** Снимок без индекса звёзд и long-tail: C4 в этом случае неактивна. */
    public RepoIndexSnapshot(
            TimeWindow window,
            Instant builtAt,
            int hoursLoaded,
            List<Instant> missingHours,
            int parseErrors,
            Map<String, IndexedRepo> repos) {
        this(window, builtAt, hoursLoaded, missingHours, parseErrors, repos, Map.of(), Map.of());
    }

    /**
     * Репозиторий из топа, а если его там нет — из long-tail окна: агрегаты по часам
     * берутся из дайджестов (часы без событий — пустые). Пусто — в окне у репозитория
     * нет ни одного события, отвечать нечем.
     */
    public Optional<IndexHit> lookup(RepoRef repo) {
        String key = GhArchiveParser.normalizeFullName(repo.fullName());
        IndexedRepo top = repos.get(key);
        if (top != null) {
            return Optional.of(new IndexHit(top, DataSource.Tier.TOP));
        }
        boolean found = false;
        String displayName = null;
        List<RepoHourAggregate> hours = new ArrayList<>(window.hours());
        for (Instant hour : window.hourStarts()) {
            HourDigest digest = digests.get(hour);
            RepoHourAggregate aggregate = digest == null ? null : digest.repos().get(key);
            if (aggregate == null) {
                hours.add(RepoHourAggregate.empty(hour));
                continue;
            }
            found = true;
            if (displayName == null) {
                displayName = digest.displayNames().get(key);
            }
            hours.add(aggregate);
        }
        if (!found) {
            return Optional.empty();
        }
        return Optional.of(new IndexHit(new IndexedRepo(refOf(displayName, repo), hours), DataSource.Tier.LONG_TAIL));
    }

    /** Копия снимка, где репозиторий добавлен в топ (закрепление, F4-2). */
    public RepoIndexSnapshot withRepo(String normalizedName, IndexedRepo repo) {
        Map<String, IndexedRepo> withRepo = new HashMap<>(repos);
        withRepo.put(normalizedName, repo);
        return new RepoIndexSnapshot(
                window, builtAt, hoursLoaded, missingHours, parseErrors, withRepo, starIndexByHour, digests);
    }

    /** Сколько разных репозиториев с событиями в окне: топ + long-tail. */
    public int windowRepoCount() {
        Set<String> names = new HashSet<>(repos.keySet());
        digests.values().forEach(digest -> names.addAll(digest.repos().keySet()));
        return names.size();
    }

    /** Имя в исходном регистре из GH Archive; если оно не похоже на owner/name — как в запросе. */
    private static RepoRef refOf(String displayName, RepoRef requested) {
        if (displayName == null) {
            return requested;
        }
        int slash = displayName.indexOf('/');
        if (slash <= 0 || slash == displayName.length() - 1) {
            return requested;
        }
        return new RepoRef(displayName.substring(0, slash), displayName.substring(slash + 1));
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

    public int rank(String fullName) {
        IndexedRepo target = repos.get(GhArchiveParser.normalizeFullName(fullName));
        if (target == null) {
            return -1;
        }
        List<IndexedRepo> ranked = top(repos.size());
        for (int i = 0; i < ranked.size(); i++) {
            if (ranked.get(i) == target) {
                return i + 1;
            }
        }
        return -1;
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
