package ru.itmo.ipkn.starguard.core.archive;

import ru.itmo.ipkn.starguard.core.domain.GhEvent;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/** Результат загрузки окна из GH Archive: события плюс служебная сводка. */
public record ArchiveFetchResult(
        List<GhEvent> events,
        int hoursRequested,
        int hoursLoaded,
        List<Instant> missingHours,
        int parseErrors,
        boolean cached
) {

    public ArchiveFetchResult {
        events = List.copyOf(Objects.requireNonNull(events, "events"));
        missingHours = List.copyOf(Objects.requireNonNull(missingHours, "missingHours"));
    }
}
