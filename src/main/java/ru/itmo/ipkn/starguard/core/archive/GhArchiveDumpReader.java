package ru.itmo.ipkn.starguard.core.archive;

import ru.itmo.ipkn.starguard.core.domain.GhEvent;

import java.io.IOException;
import java.io.InputStream;
import java.util.function.Consumer;
import java.util.zip.GZIPInputStream;

/**
 * Общий путь чтения часового дампа: gzip → строки байтами → события с ранним фильтром.
 * Используется и индексом ({@link HttpGhArchiveBulkReader}), и запросом одного
 * репозитория ({@link HttpGhArchiveClient}).
 */
public final class GhArchiveDumpReader {

    /** Самая длинная строка в реальных дампах — сотни КБ; 16 МиБ — с большим запасом. */
    public static final int MAX_LINE_BYTES = 16 * 1024 * 1024;

    /** У {@link GZIPInputStream} по умолчанию буфер 512 байт — слишком мелкие чтения из сети. */
    static final int GZIP_BUFFER_BYTES = 64 * 1024;

    private GhArchiveDumpReader() {
    }

    /**
     * Читает gzip-дамп и передаёт в {@code sink} события, которые пропустил {@code filter}.
     *
     * @return число строк, которые не удалось разобрать (битый JSON или слишком длинная строка)
     */
    public static int read(InputStream gzipped, GhArchiveParser parser, EventFilter filter,
                           Consumer<GhEvent> sink) throws IOException {
        LineCounter errors = new LineCounter();
        try (GZIPInputStream gzip = new GZIPInputStream(gzipped, GZIP_BUFFER_BYTES)) {
            int oversized = new NdjsonLineReader(MAX_LINE_BYTES).forEachLine(gzip, (buffer, offset, length) -> {
                try {
                    parser.parse(buffer, offset, length, filter).ifPresent(sink);
                } catch (MalformedEventException e) {
                    errors.count++;
                }
            });
            return errors.count + oversized;
        }
    }

    private static final class LineCounter {
        private int count;
    }
}
