package ru.itmo.ipkn.starguard.core.archive;

import ru.itmo.ipkn.starguard.core.domain.GhEvent;
import ru.itmo.ipkn.starguard.core.domain.StarIndex;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Результат загрузки окна из GH Archive: события репозитория, служебная сводка
 * и глобальный индекс звёзд окна (для C4; пустой, если не собирался).
 */
public record ArchiveFetchResult(
        List<GhEvent> events,
        int hoursRequested,
        int hoursLoaded,
        List<Instant> missingHours,
        int parseErrors,
        boolean cached,
        StarIndex starIndex
) {

    public ArchiveFetchResult {
        events = List.copyOf(Objects.requireNonNull(events, "events"));
        missingHours = List.copyOf(Objects.requireNonNull(missingHours, "missingHours"));
        starIndex = starIndex == null ? StarIndex.empty() : starIndex;
    }

    public ArchiveFetchResult(
            List<GhEvent> events,
            int hoursRequested,
            int hoursLoaded,
            List<Instant> missingHours,
            int parseErrors,
            boolean cached) {
        this(events, hoursRequested, hoursLoaded, missingHours, parseErrors, cached, StarIndex.empty());
    }
}
