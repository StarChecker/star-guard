package ru.itmo.ipkn.starguard.core.archive;

import java.time.Instant;

/** Результат потокового чтения одного часового дампа для индекса. */
public record HourReadResult(
        Instant hour,
        int parseErrors,
        boolean missing,
        boolean failed
) {
}
