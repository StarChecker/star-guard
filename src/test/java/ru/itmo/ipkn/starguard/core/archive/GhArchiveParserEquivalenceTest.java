package ru.itmo.ipkn.starguard.core.archive;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import ru.itmo.ipkn.starguard.core.domain.GhEvent;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Потоковый парсер обязан давать тот же результат, что прежний {@code readTree}:
 * то же событие, тот же пропуск, та же ошибка разбора. С ранним фильтром — то же,
 * что прежний разбор плюс фильтрация; единственное допустимое расхождение —
 * битая строка, отброшенная фильтром до места поломки, не считается ошибкой.
 */
class GhArchiveParserEquivalenceTest {

    private static final String MALFORMED = "<malformed>";
    private static final String SKIPPED = "<skipped>";
    private static final String CRASH = "<crash>";

    private static final Map<String, EventFilter> FILTERS = new LinkedHashMap<>();

    static {
        FILTERS.put("all", EventFilter.ALL);
        FILTERS.put("stars", EventFilter.stars());
        FILTERS.put("repo demo/star-guard", EventFilter.repo("demo/star-guard"));
        FILTERS.put("repo A/B (case)", EventFilter.repo("A/B"));
        FILTERS.put("repos {a/b, demo/star-guard}", EventFilter.repos(Set.of("a/b", "demo/star-guard")));
        FILTERS.put("repo a/b or stars", EventFilter.repo("a/b").or(EventFilter.stars()));
    }

    private final LegacyGhArchiveParser legacy = new LegacyGhArchiveParser();
    private final GhArchiveParser parser = new GhArchiveParser();

    @Test
    void matchesLegacyParserOnEdgeCases() {
        assertEquivalent(EquivalenceCorpus.lines());
    }

    @Test
    void matchesLegacyParserOnAllFixtures() throws IOException {
        List<byte[]> lines = new ArrayList<>();
        Resource[] resources = new PathMatchingResourcePatternResolver()
                .getResources("classpath*:fixtures/**/*.jsonl");
        for (Resource resource : resources) {
            try (InputStream in = resource.getInputStream()) {
                readLines(in, lines);
            }
        }

        assertThat(resources).isNotEmpty();
        assertThat(lines).hasSizeGreaterThan(500);
        assertEquivalent(lines);
    }

    @Test
    void decodesInvalidUtf8LikeLegacyReader() {
        byte[] prefix = "{\"id\":\"1\",\"type\":\"WatchEvent\",\"actor\":{\"id\":7,\"login\":\"de"
                .getBytes(StandardCharsets.UTF_8);
        byte[] broken = {(byte) 0xC3, (byte) 0x28};
        byte[] suffix = ("v\"},\"repo\":{\"id\":42,\"name\":\"a/b\"},"
                + "\"payload\":{\"x\":\"" + "é" + "\"},\"created_at\":\"2026-11-09T10:15:30Z\"}")
                .getBytes(StandardCharsets.UTF_8);
        byte[] line = concat(prefix, broken, suffix);

        assertEquivalent(List.of(line));
        GhEvent event = parser.parse(line, 0, line.length, EventFilter.ALL).orElseThrow();
        assertThat(event.actorLogin()).contains("�");
    }

    /**
     * Сверка на реальных часовых дампах: каталог с {@code *.json.gz} передаётся через
     * {@code -Dgharchive.dumps=/path}. В CI не запускается — дампов в репозитории нет.
     */
    @Test
    @EnabledIfSystemProperty(named = "gharchive.dumps", matches = ".+")
    void matchesLegacyParserOnRealDumps() throws IOException {
        Path dir = Path.of(System.getProperty("gharchive.dumps"));
        List<Path> dumps;
        try (Stream<Path> files = Files.list(dir)) {
            dumps = files.filter(path -> path.toString().endsWith(".json.gz")).sorted().toList();
        }
        assertThat(dumps).isNotEmpty();

        long total = 0;
        for (Path dump : dumps) {
            List<byte[]> lines = new ArrayList<>();
            try (InputStream in = new GZIPInputStream(Files.newInputStream(dump))) {
                readLines(in, lines);
            }
            assertEquivalent(lines);
            total += lines.size();
        }
        System.out.printf("Equivalence on real dumps: %d files, %d lines, %d filters%n",
                dumps.size(), total, FILTERS.size());
    }

    @Test
    void skipsLinesThatCrashedLegacyParser() {
        // Jackson 3: asText() у ObjectNode бросает исключение, и прежний парсер ронял весь час.
        String line = "{\"id\":{\"a\":1},\"type\":\"WatchEvent\",\"actor\":{\"id\":7},"
                + "\"repo\":{\"id\":42,\"name\":\"a/b\"},\"created_at\":\"2026-11-09T10:15:30Z\"}";

        assertThat(outcome(() -> legacy.parse(line))).asString().startsWith(CRASH);
        assertThat(outcome(() -> parser.parse(line))).isEqualTo(SKIPPED);
    }

    private void assertEquivalent(List<byte[]> lines) {
        for (byte[] bytes : lines) {
            String line = new String(bytes, StandardCharsets.UTF_8);
            Object expected = outcome(() -> legacy.parse(line));
            if (expected instanceof String text && text.startsWith(CRASH)) {
                // Прежний парсер падал необработанным исключением на значении-объекте там,
                // где ждал строку; новый такую строку просто пропускает.
                expected = SKIPPED;
            }

            assertThat(outcome(() -> parser.parse(line)))
                    .as("string path: %s", line)
                    .isEqualTo(expected);

            byte[] padded = concat("garbage\n".getBytes(StandardCharsets.UTF_8), bytes,
                    "\n{tail".getBytes(StandardCharsets.UTF_8));
            int offset = "garbage\n".length();

            for (Map.Entry<String, EventFilter> entry : FILTERS.entrySet()) {
                EventFilter filter = entry.getValue();
                Object actual = outcome(() -> parser.parse(padded, offset, bytes.length, filter));
                Object filtered = expected instanceof GhEvent event && !filter.accepts(event)
                        ? SKIPPED
                        : expected;
                if (MALFORMED.equals(expected) && filter != EventFilter.ALL) {
                    assertThat(actual)
                            .as("filter '%s' on malformed line: %s", entry.getKey(), line)
                            .isIn(MALFORMED, SKIPPED);
                } else {
                    assertThat(actual)
                            .as("filter '%s': %s", entry.getKey(), line)
                            .isEqualTo(filtered);
                }
            }
        }
    }

    private static Object outcome(Supplier<Optional<GhEvent>> parse) {
        try {
            return parse.get().map(Object.class::cast).orElse(SKIPPED);
        } catch (MalformedEventException e) {
            return MALFORMED;
        } catch (RuntimeException e) {
            return CRASH + e.getClass().getSimpleName();
        }
    }

    /** Строки как их видел прежний путь: {@code readLine}, без пустых. */
    private static void readLines(InputStream in, List<byte[]> lines) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
        String line;
        while ((line = reader.readLine()) != null) {
            if (!line.isBlank()) {
                lines.add(line.getBytes(StandardCharsets.UTF_8));
            }
        }
    }

    private static byte[] concat(byte[]... parts) {
        int length = Arrays.stream(parts).mapToInt(part -> part.length).sum();
        byte[] result = new byte[length];
        int position = 0;
        for (byte[] part : parts) {
            System.arraycopy(part, 0, result, position, part.length);
            position += part.length;
        }
        return result;
    }
}
