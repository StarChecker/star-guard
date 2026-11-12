package ru.itmo.ipkn.starguard.core.archive;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NdjsonLineReaderTest {

    @Test
    void splitsLinesDropsCarriageReturnAndBlankLines() throws IOException {
        Lines lines = read(new NdjsonLineReader(1024), "a\r\nbb\n\n  \t\ncc\n\u001f\nlast-without-newline");

        assertThat(lines.values).containsExactly("a", "bb", "cc", "last-without-newline");
        assertThat(lines.oversized).isZero();
    }

    @Test
    void handlesLinesSpanningReadsAndBufferGrowth() throws IOException {
        String longLine = "x".repeat(10_000);
        String content = "first\n" + longLine + "\nsecond\n" + longLine + "y\n";

        // Начальный буфер 4 байта и поток, который отдаёт по 1–3 байта за чтение.
        Lines lines = read(new NdjsonLineReader(20_000, 4), trickle(content));

        assertThat(lines.values).containsExactly("first", longLine, "second", longLine + "y");
    }

    @Test
    void skipsAndCountsOversizedLines() throws IOException {
        String content = "ok-1\n" + "z".repeat(11) + "\nok-2\n" + "w".repeat(25) + "\nexactly-10\n" + "q".repeat(12);

        Lines lines = read(new NdjsonLineReader(10, 4), trickle(content));

        assertThat(lines.values).containsExactly("ok-1", "ok-2", "exactly-10");
        assertThat(lines.oversized).isEqualTo(3);
    }

    @Test
    void emptyStreamProducesNoLines() throws IOException {
        Lines lines = read(new NdjsonLineReader(16), "");

        assertThat(lines.values).isEmpty();
        assertThat(lines.oversized).isZero();
    }

    @Test
    void rejectsInvalidLimits() {
        assertThatThrownBy(() -> new NdjsonLineReader(0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new NdjsonLineReader(Integer.MAX_VALUE))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static Lines read(NdjsonLineReader reader, String content) throws IOException {
        return read(reader, new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)));
    }

    private static Lines read(NdjsonLineReader reader, InputStream in) throws IOException {
        Lines lines = new Lines();
        lines.oversized = reader.forEachLine(in, (buffer, offset, length) ->
                lines.values.add(new String(buffer, offset, length, StandardCharsets.UTF_8)));
        return lines;
    }

    private static InputStream trickle(String content) {
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        return new InputStream() {
            private int position;
            private int step;

            @Override
            public int read() {
                return position < bytes.length ? bytes[position++] & 0xFF : -1;
            }

            @Override
            public int read(byte[] target, int offset, int length) {
                if (position >= bytes.length) {
                    return -1;
                }
                int chunk = Math.min(Math.min(length, 1 + step++ % 3), bytes.length - position);
                System.arraycopy(bytes, position, target, offset, chunk);
                position += chunk;
                return chunk;
            }
        };
    }

    private static final class Lines {
        private final List<String> values = new ArrayList<>();
        private int oversized;
    }
}
