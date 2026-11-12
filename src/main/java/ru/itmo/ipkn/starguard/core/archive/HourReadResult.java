package ru.itmo.ipkn.starguard.core.archive;

import java.time.Instant;

/**
 * Результат потокового чтения одного часового дампа для индекса.
 *
 * @param lines      строк в дампе (включая битые), 0 — если час не прочитан
 * @param bytes      распакованных байт в этих строках
 * @param parseNanos время чтения и разбора дампа (поток из сети, gzip, JSON)
 */
public record HourReadResult(
        Instant hour,
        int parseErrors,
        boolean missing,
        boolean failed,
        long lines,
        long bytes,
        long parseNanos
) {

    public HourReadResult(Instant hour, int parseErrors, boolean missing, boolean failed) {
        this(hour, parseErrors, missing, failed, 0, 0, 0);
    }
}
