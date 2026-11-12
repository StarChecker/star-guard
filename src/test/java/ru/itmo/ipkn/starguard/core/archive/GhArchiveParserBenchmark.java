package ru.itmo.ipkn.starguard.core.archive;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import ru.itmo.ipkn.starguard.core.domain.EventType;
import ru.itmo.ipkn.starguard.core.domain.GhEvent;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Бенчмарк F3-1: прежний конвейер ({@code GZIPInputStream} → {@code readLine} →
 * {@code readTree}) против потокового ({@link GhArchiveDumpReader}) на реальных дампах.
 * <p>
 * Запуск: {@code ./mvnw test -Dtest=GhArchiveParserBenchmark -Dgharchive.benchmark=/dir/with/json.gz}.
 * Печатает таблицу в Markdown и пишет её в {@code target/gharchive-parser-benchmark.md}.
 * Заодно сверяет, что оба конвейера отдают одинаковые события.
 */
@EnabledIfSystemProperty(named = "gharchive.benchmark", matches = ".+")
class GhArchiveParserBenchmark {

    private static final int WARMUP = 5;
    private static final int RUNS = 15;

    private final LegacyGhArchiveParser legacy = new LegacyGhArchiveParser();
    private final GhArchiveParser parser = new GhArchiveParser();

    @Test
    void compareLegacyAndStreamingPipelines() throws IOException {
        List<byte[]> gzipped = new ArrayList<>();
        List<byte[]> raw = new ArrayList<>();
        try (Stream<Path> files = Files.list(Path.of(System.getProperty("gharchive.benchmark")))) {
            for (Path dump : files.filter(path -> path.toString().endsWith(".json.gz")).sorted().toList()) {
                byte[] bytes = Files.readAllBytes(dump);
                gzipped.add(bytes);
                try (InputStream in = new GZIPInputStream(new ByteArrayInputStream(bytes))) {
                    raw.add(in.readAllBytes());
                }
            }
        }
        assertThat(gzipped).isNotEmpty();
        long rawBytes = raw.stream().mapToLong(bytes -> bytes.length).sum();
        long gzBytes = gzipped.stream().mapToLong(bytes -> bytes.length).sum();

        Workload workload = workload(raw);
        Map<String, EventFilter> scenarios = new LinkedHashMap<>();
        scenarios.put("полный разбор (без фильтра)", EventFilter.ALL);
        scenarios.put("проход 1 индекса: только звёзды", EventFilter.stars());
        scenarios.put("проход 2 индекса: %d репозиториев со звёздами".formatted(workload.starred().size()),
                EventFilter.repos(workload.starred()));
        scenarios.put("один репозиторий + звёзды для C4", EventFilter.repo(workload.mostActive())
                .or(EventFilter.stars()));

        // Сверка результатов: потоковый конвейер = прежний + фильтр.
        for (EventFilter filter : scenarios.values()) {
            assertThat(streaming(gzipped, true, filter)).isEqualTo(legacy(gzipped, true, filter));
        }

        List<Row> rows = new ArrayList<>();
        rows.add(measure("распаковка gzip без разбора", gzipped, true, null));
        rows.add(measure("прежний: readLine + readTree", gzipped, true, EventFilter.ALL, true));
        scenarios.forEach((name, filter) -> rows.add(measure("потоковый: " + name, gzipped, true, filter)));
        rows.add(measure("прежний без gzip: readLine + readTree", raw, false, EventFilter.ALL, true));
        scenarios.forEach((name, filter) -> rows.add(measure("потоковый без gzip: " + name, raw, false, filter)));

        String report = report(rows, gzipped.size(), workload.lines(), gzBytes, rawBytes);
        System.out.println(report);
        Files.writeString(Path.of("target", "gharchive-parser-benchmark.md"), report);
    }

    private Row measure(String name, List<byte[]> data, boolean gzip, EventFilter filter) {
        return measure(name, data, gzip, filter, false);
    }

    private Row measure(String name, List<byte[]> data, boolean gzip, EventFilter filter, boolean useLegacy) {
        Runnable run = () -> {
            try {
                if (filter == null) {
                    for (byte[] bytes : data) {
                        try (InputStream in = new GZIPInputStream(new ByteArrayInputStream(bytes))) {
                            in.transferTo(OutputSink.INSTANCE);
                        }
                    }
                } else if (useLegacy) {
                    legacy(data, gzip, filter);
                } else {
                    streaming(data, gzip, filter);
                }
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        };
        for (int i = 0; i < WARMUP; i++) {
            run.run();
        }
        long[] nanos = new long[RUNS];
        long[] allocated = new long[RUNS];
        com.sun.management.ThreadMXBean threads =
                (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
        for (int i = 0; i < RUNS; i++) {
            long allocatedBefore = threads.getCurrentThreadAllocatedBytes();
            long start = System.nanoTime();
            run.run();
            nanos[i] = System.nanoTime() - start;
            allocated[i] = threads.getCurrentThreadAllocatedBytes() - allocatedBefore;
        }
        Arrays.sort(nanos);
        Arrays.sort(allocated);
        return new Row(name, gzip, filter == null, useLegacy, nanos[RUNS / 2] / 1_000_000.0,
                allocated[RUNS / 2] / (1024.0 * 1024.0));
    }

    /** Прежний конвейер: буфер gzip по умолчанию, декодирование строк, дерево на каждую строку. */
    private Map<String, Integer> legacy(List<byte[]> data, boolean gzip, EventFilter filter) throws IOException {
        Counter counter = new Counter();
        for (byte[] bytes : data) {
            InputStream source = new ByteArrayInputStream(bytes);
            try (InputStream in = gzip ? new GZIPInputStream(source) : source;
                 BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.isBlank()) {
                        continue;
                    }
                    try {
                        Optional<GhEvent> event = legacy.parse(line);
                        if (event.isPresent() && filter.accepts(event.get())) {
                            counter.accept(event.get());
                        }
                    } catch (MalformedEventException e) {
                        counter.errors++;
                    }
                }
            }
        }
        return counter.result();
    }

    private Map<String, Integer> streaming(List<byte[]> data, boolean gzip, EventFilter filter) throws IOException {
        Counter counter = new Counter();
        for (byte[] bytes : data) {
            if (gzip) {
                counter.errors += GhArchiveDumpReader.read(new ByteArrayInputStream(bytes), parser, filter, counter);
            } else {
                new NdjsonLineReader(GhArchiveDumpReader.MAX_LINE_BYTES).forEachLine(
                        new ByteArrayInputStream(bytes), (buffer, offset, length) -> {
                            try {
                                parser.parse(buffer, offset, length, filter).ifPresent(counter);
                            } catch (MalformedEventException e) {
                                counter.errors++;
                            }
                        });
            }
        }
        return counter.result();
    }

    private Workload workload(List<byte[]> raw) {
        Map<String, Integer> eventsByRepo = new HashMap<>();
        Set<String> starred = new HashSet<>();
        int lines = 0;
        for (byte[] bytes : raw) {
            for (String line : new String(bytes, StandardCharsets.UTF_8).split("\n")) {
                if (line.isBlank()) {
                    continue;
                }
                lines++;
                Optional<GhEvent> event = legacy.parse(line);
                if (event.isPresent()) {
                    eventsByRepo.merge(event.get().repoFullName(), 1, Integer::sum);
                    if (event.get().type() == EventType.WATCH) {
                        starred.add(GhArchiveParser.normalizeFullName(event.get().repoFullName()));
                    }
                }
            }
        }
        String mostActive = eventsByRepo.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .orElseThrow()
                .getKey();
        return new Workload(lines, Set.copyOf(starred), mostActive);
    }

    private static String report(List<Row> rows, int files, int lines, long gzBytes, long rawBytes) {
        double legacyGzip = rows.stream().filter(row -> row.legacy() && row.gzip()).findFirst()
                .orElseThrow().millis();
        double legacyRaw = rows.stream().filter(row -> row.legacy() && !row.gzip()).findFirst()
                .orElseThrow().millis();
        StringBuilder out = new StringBuilder();
        out.append("Дампы: %d файлов, %d строк, %.1f МБ gzip / %.1f МБ JSON; медиана из %d прогонов после %d разогревочных%n%n"
                .formatted(files, lines, gzBytes / 1e6, rawBytes / 1e6, RUNS, WARMUP));
        out.append("| Конвейер | Время, мс | МБ JSON/с | Аллокации, МБ | Ускорение |\n");
        out.append("|---|---:|---:|---:|---:|\n");
        for (Row row : rows) {
            double base = row.gzip() ? legacyGzip : legacyRaw;
            String speedup = row.gunzipOnly() ? "—" : "%.1f×".formatted(base / row.millis());
            out.append("| %s | %.1f | %.0f | %.1f | %s |%n".formatted(
                    row.name(), row.millis(), rawBytes / 1e6 / (row.millis() / 1000.0), row.allocatedMb(), speedup));
        }
        return out.toString();
    }

    private record Row(String name, boolean gzip, boolean gunzipOnly, boolean legacy,
                       double millis, double allocatedMb) {
    }

    private record Workload(int lines, Set<String> starred, String mostActive) {
    }

    private static final class Counter implements Consumer<GhEvent> {
        private final Map<String, Integer> byType = new HashMap<>();
        private int errors;

        @Override
        public void accept(GhEvent event) {
            byType.merge(event.type() + "|" + event.repoFullName() + "|" + event.eventId(), 1, Integer::sum);
        }

        private Map<String, Integer> result() {
            Map<String, Integer> result = new HashMap<>(byType);
            result.put("<errors>", errors);
            return result;
        }
    }

    private static final class OutputSink extends java.io.OutputStream {
        private static final OutputSink INSTANCE = new OutputSink();

        @Override
        public void write(int b) {
        }

        @Override
        public void write(byte[] b, int off, int len) {
        }
    }
}
