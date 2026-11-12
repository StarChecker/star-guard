package ru.itmo.ipkn.starguard.core.index;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import ru.itmo.ipkn.starguard.core.archive.EventFilter;
import ru.itmo.ipkn.starguard.core.archive.GhArchiveBulkReader;
import ru.itmo.ipkn.starguard.core.archive.GhArchiveDumpReader;
import ru.itmo.ipkn.starguard.core.archive.GhArchiveParser;
import ru.itmo.ipkn.starguard.core.archive.HourReadResult;
import ru.itmo.ipkn.starguard.core.archive.TimeWindowCalculator;
import ru.itmo.ipkn.starguard.core.config.ArchiveProperties;
import ru.itmo.ipkn.starguard.core.config.RepoIndexProperties;
import ru.itmo.ipkn.starguard.core.domain.EventType;
import ru.itmo.ipkn.starguard.core.domain.GhEvent;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Бенчмарк F3-2 на реальных часах GH Archive без сети: каталог {@code yyyy-MM-dd-H.json.gz}
 * передаётся через {@code -Dgharchive.window=/dir}, окно — 6 часов, нужно 7 часов подряд
 * (по умолчанию 2026-11-10 12…18 UTC, начало меняется {@code -Dgharchive.window.start}).
 * <p>
 * Сравнивает первую сборку, сдвиг окна на час и полную пересборку сдвинутого окна;
 * проверяет, что инкрементальный результат совпадает с полной пересборкой, и для
 * сравнения прогоняет прежнюю схему F3-1 (два прохода по каждому часу).
 */
@EnabledIfSystemProperty(named = "gharchive.window", matches = ".+")
class RepoIndexBuilderBenchmark {

    private static final DateTimeFormatter HOUR_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd-H", Locale.ROOT);
    private static final int RUNS = 7;
    private static final RepoIndexProperties INDEX = new RepoIndexProperties(true, 1000, 6, false, "0 10 * * * *");
    /** Один поток: сравниваем объём работы, а не число ядер (в проде часы читаются в 4 потока). */
    private static final ArchiveProperties ARCHIVE = new ArchiveProperties(
            "https://example.test", ArchiveProperties.Mode.HTTP,
            6, 24, 2, 1, 0, false, Duration.ofHours(1), null, null);

    @Test
    void compareFirstBuildSlideAndFullRebuild() throws IOException {
        Instant start = Instant.parse(System.getProperty("gharchive.window.start", "2026-11-10T12:00:00Z"));
        LocalDumps dumps = LocalDumps.load(Path.of(System.getProperty("gharchive.window")), start, 7);
        // Окно [start, start+6h): часы lag=2 назад от «сейчас».
        Clock first = Clock.fixed(start.plus(Duration.ofHours(8)), ZoneOffset.UTC);
        Clock slid = Clock.fixed(start.plus(Duration.ofHours(9)), ZoneOffset.UTC);

        for (int i = 0; i < 2; i++) {
            builder(dumps, first).build();
        }

        List<Long> firstBuild = new ArrayList<>();
        List<Long> slide = new ArrayList<>();
        List<Long> rebuild = new ArrayList<>();
        List<Long> legacy = new ArrayList<>();
        int firstReads = 0;
        int slideReads = 0;
        int rebuildReads = 0;
        int legacyReads = 0;
        for (int run = 0; run < RUNS; run++) {
            MutableClock clock = new MutableClock(first.instant());
            RepoIndexBuilder incremental = builder(dumps, clock);
            dumps.reads.set(0);
            firstBuild.add(timed(incremental::build));
            firstReads = dumps.reads.getAndSet(0);

            clock.set(slid.instant());
            RepoIndexSnapshot[] slidSnapshot = new RepoIndexSnapshot[1];
            slide.add(timed(() -> slidSnapshot[0] = incremental.build()));
            slideReads = dumps.reads.getAndSet(0);

            RepoIndexSnapshot[] fullSnapshot = new RepoIndexSnapshot[1];
            rebuild.add(timed(() -> fullSnapshot[0] = builder(dumps, slid).build()));
            rebuildReads = dumps.reads.getAndSet(0);
            assertThat(IndexSnapshots.describe(slidSnapshot[0]))
                    .isEqualTo(IndexSnapshots.describe(fullSnapshot[0]));

            legacy.add(timed(() -> legacyTwoPasses(dumps, slid)));
            legacyReads = dumps.reads.getAndSet(0);
        }

        long heapBefore = usedHeapAfterGc();
        RepoIndexBuilder retainedBuilder = builder(dumps, first);
        RepoIndexSnapshot retainedSnapshot = retainedBuilder.build();
        long heapAfter = usedHeapAfterGc();
        int repoHours = retainedSnapshot.window().hourStarts().size();
        assertThat(retainedBuilder.retainedHours()).hasSize(repoHours);

        String report = """
                Окно 6 ч, топ-%d; %d реальных часов GH Archive с %s; один поток; медиана из %d прогонов.

                | Сценарий | Чтений часа | Время, мс |
                |---|---:|---:|
                | прежняя схема F3-1: 2 прохода × 6 часов (на каждую пересборку) | %d | %d |
                | F3-2: первая сборка, 1 проход × 6 часов | %d | %d |
                | F3-2: сдвиг окна на час (инкрементально) | %d | %d |
                | F3-2: полная пересборка сдвинутого окна (для сверки) | %d | %d |

                Инкрементальный снимок совпал с полной пересборкой во всех %d прогонах.
                Дайджесты 6 часов в памяти: ≈ %.1f МБ (heap после GC, грубая оценка).
                """.formatted(INDEX.topN(), dumps.hours(), start, RUNS,
                legacyReads, median(legacy), firstReads, median(firstBuild),
                slideReads, median(slide), rebuildReads, median(rebuild), RUNS,
                (heapAfter - heapBefore) / (1024.0 * 1024.0));
        System.out.println(report);
        Files.writeString(Path.of("target", "index-builder-benchmark.md"), report);
        assertThat(retainedSnapshot.repos()).isNotEmpty();
    }

    /** Прежняя схема F3-1: проход 1 — звёзды всех часов, проход 2 — топ-N по каждому часу. */
    private void legacyTwoPasses(LocalDumps dumps, Clock clock) {
        List<Instant> hours = new TimeWindowCalculator(clock, ARCHIVE).window(INDEX.windowHours()).hourStarts();
        Map<String, Integer> starsByRepo = new HashMap<>();
        for (Instant hour : hours) {
            dumps.readHour(hour, EventFilter.stars(), event -> {
                if (event.type() == EventType.WATCH) {
                    starsByRepo.merge(GhArchiveParser.normalizeFullName(event.repoFullName()), 1, Integer::sum);
                }
            });
        }
        Set<String> candidates = TopRepoSelector.select(starsByRepo, INDEX.topN());
        AtomicInteger events = new AtomicInteger();
        for (Instant hour : hours) {
            dumps.readHour(hour, EventFilter.repos(candidates), event -> events.incrementAndGet());
        }
        assertThat(events.get()).isPositive();
    }

    private RepoIndexBuilder builder(LocalDumps dumps, Clock clock) {
        return new RepoIndexBuilder(dumps, new TimeWindowCalculator(clock, ARCHIVE), ARCHIVE, INDEX, clock);
    }

    private static long timed(Runnable action) {
        long startedAt = System.nanoTime();
        action.run();
        return Duration.ofNanos(System.nanoTime() - startedAt).toMillis();
    }

    private static long median(List<Long> values) {
        long[] sorted = values.stream().mapToLong(Long::longValue).toArray();
        Arrays.sort(sorted);
        return sorted[sorted.length / 2];
    }

    private static long usedHeapAfterGc() {
        for (int i = 0; i < 3; i++) {
            System.gc();
        }
        return ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed();
    }

    /** Часы GH Archive из локальных файлов: тот же путь разбора, что у HTTP-ридера, без сети. */
    private static final class LocalDumps implements GhArchiveBulkReader {

        private final GhArchiveParser parser = new GhArchiveParser();
        private final Map<Instant, byte[]> files;
        private final AtomicInteger reads = new AtomicInteger();

        private LocalDumps(Map<Instant, byte[]> files) {
            this.files = files;
        }

        static LocalDumps load(Path dir, Instant start, int hours) throws IOException {
            Map<Instant, byte[]> files = new ConcurrentHashMap<>();
            for (int i = 0; i < hours; i++) {
                Instant hour = start.plus(Duration.ofHours(i));
                Path file = dir.resolve(HOUR_FORMAT.format(hour.atZone(ZoneOffset.UTC)) + ".json.gz");
                files.put(hour, Files.readAllBytes(file));
            }
            return new LocalDumps(files);
        }

        private int hours() {
            return files.size();
        }

        @Override
        public HourReadResult readHour(Instant hour, Consumer<GhEvent> sink) {
            return readHour(hour, EventFilter.ALL, sink);
        }

        @Override
        public HourReadResult readHour(Instant hour, EventFilter filter, Consumer<GhEvent> sink) {
            reads.incrementAndGet();
            byte[] bytes = files.get(hour);
            if (bytes == null) {
                return new HourReadResult(hour, 0, true, false);
            }
            Instant hourEnd = hour.plusSeconds(3600);
            try {
                int errors = GhArchiveDumpReader.read(new ByteArrayInputStream(bytes), parser, filter, event -> {
                    if (!event.createdAt().isBefore(hour) && event.createdAt().isBefore(hourEnd)) {
                        sink.accept(event);
                    }
                });
                return new HourReadResult(hour, errors, false, false);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }

    private static final class MutableClock extends Clock {

        private volatile Instant now;

        private MutableClock(Instant now) {
            this.now = now;
        }

        private void set(Instant now) {
            this.now = now;
        }

        @Override
        public java.time.ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
