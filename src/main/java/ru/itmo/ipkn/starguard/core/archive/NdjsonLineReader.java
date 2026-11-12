package ru.itmo.ipkn.starguard.core.archive;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

/**
 * Режет поток NDJSON на строки, не декодируя байты в {@code String}.
 * <p>
 * Буфер переиспользуется и растёт под самую длинную строку, но не больше
 * {@code maxLineBytes}: строка длиннее пропускается до следующего {@code \n}
 * целиком и учитывается как битая — один мусорный дамп не съест heap.
 * Разделитель — {@code \n}, завершающий {@code \r} отбрасывается; пустые
 * и пробельные строки обработчику не передаются.
 */
public final class NdjsonLineReader {

    /** Обработчик строки: байты в {@code buffer[offset, offset + length)} валидны только во время вызова. */
    @FunctionalInterface
    public interface LineHandler {
        void line(byte[] buffer, int offset, int length);
    }

    static final int DEFAULT_INITIAL_BUFFER = 64 * 1024;

    /** Предел размера массива в JVM с запасом на заголовок. */
    private static final int MAX_ARRAY_BYTES = Integer.MAX_VALUE - 8;

    private final int maxLineBytes;
    private final int initialBuffer;

    public NdjsonLineReader(int maxLineBytes) {
        this(maxLineBytes, DEFAULT_INITIAL_BUFFER);
    }

    NdjsonLineReader(int maxLineBytes, int initialBuffer) {
        if (maxLineBytes < 1 || maxLineBytes > MAX_ARRAY_BYTES - 1 || initialBuffer < 1) {
            throw new IllegalArgumentException(
                    "maxLineBytes must be within [1, " + (MAX_ARRAY_BYTES - 1) + "], initialBuffer positive");
        }
        this.maxLineBytes = maxLineBytes;
        this.initialBuffer = Math.min(initialBuffer, maxLineBytes + 1);
    }

    /**
     * Передаёт обработчику каждую непустую строку потока.
     *
     * @return сколько строк пропущено из-за превышения {@code maxLineBytes}
     */
    public int forEachLine(InputStream in, LineHandler handler) throws IOException {
        byte[] buffer = new byte[initialBuffer];
        int start = 0;
        int end = 0;
        int scan = 0;
        boolean skipping = false;
        int oversized = 0;

        while (true) {
            int newline = indexOfNewline(buffer, scan, end);
            if (newline >= 0) {
                if (!skipping) {
                    emit(buffer, start, newline, handler);
                }
                skipping = false;
                start = newline + 1;
                scan = start;
                continue;
            }

            if (skipping) {
                start = 0;
                end = 0;
            } else if (end - start > maxLineBytes) {
                skipping = true;
                oversized++;
                start = 0;
                end = 0;
            } else if (start > 0) {
                System.arraycopy(buffer, start, buffer, 0, end - start);
                end -= start;
                start = 0;
            }
            scan = end;
            if (end == buffer.length) {
                buffer = Arrays.copyOf(buffer, (int) Math.min(buffer.length * 2L, maxLineBytes + 1L));
            }

            int read = in.read(buffer, end, buffer.length - end);
            if (read < 0) {
                if (!skipping && end > start) {
                    if (end - start > maxLineBytes) {
                        oversized++;
                    } else {
                        emit(buffer, start, end, handler);
                    }
                }
                return oversized;
            }
            end += read;
        }
    }

    private static int indexOfNewline(byte[] buffer, int from, int to) {
        for (int i = from; i < to; i++) {
            if (buffer[i] == '\n') {
                return i;
            }
        }
        return -1;
    }

    private static void emit(byte[] buffer, int start, int end, LineHandler handler) {
        int lineEnd = end;
        if (lineEnd > start && buffer[lineEnd - 1] == '\r') {
            lineEnd--;
        }
        if (!isBlank(buffer, start, lineEnd)) {
            handler.line(buffer, start, lineEnd - start);
        }
    }

    /** Как {@code String.isBlank()} для ASCII-пробелов, включая управляющие 0x1C–0x1F. */
    private static boolean isBlank(byte[] buffer, int start, int end) {
        for (int i = start; i < end; i++) {
            byte b = buffer[i];
            boolean whitespace = b == ' ' || (b >= 0x09 && b <= 0x0D) || (b >= 0x1C && b <= 0x1F);
            if (!whitespace) {
                return false;
            }
        }
        return true;
    }
}
