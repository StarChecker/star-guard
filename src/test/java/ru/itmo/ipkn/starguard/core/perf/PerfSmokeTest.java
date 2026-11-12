package ru.itmo.ipkn.starguard.core.perf;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import ru.itmo.ipkn.starguard.core.domain.EventType;
import ru.itmo.ipkn.starguard.core.domain.GhEvent;
import ru.itmo.ipkn.starguard.core.domain.RepoRef;
import ru.itmo.ipkn.starguard.core.domain.StarIndex;
import ru.itmo.ipkn.starguard.core.domain.StarMark;
import ru.itmo.ipkn.starguard.core.domain.TimeWindow;
import ru.itmo.ipkn.starguard.core.scoring.RepoScore;
import ru.itmo.ipkn.starguard.core.scoring.ScoringEngine;
import ru.itmo.ipkn.starguard.core.service.StarTimelineService;
import ru.itmo.ipkn.starguard.core.stats.RepoAggregate;
import ru.itmo.ipkn.starguard.core.stats.StatsAggregator;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * X-4 / F3-6: perf-smoke на синтетическом датасете, офлайн.
 * <p>
 * 1. Сборка индекса звёзд C4 за 6 часов: ~300k звёзд по 40k репозиториям — время и heap.
 * 2. Запрос по горячему репозиторию: агрегаты → score всех пяти проверок (реальные C2–C4)
 *    → таймлайн; p95 сравнивается с SLO top-tier (100 мс).
 * <p>
 * Пороги в тесте с запасом против шумного CI; точные цифры — в {@code target/perf-smoke/report.md},
 * CI выкладывает его артефактом.
 */
@Tag("perf")
@SpringBootTest(properties = {
        "app.archive.mode=fixture",
        "app.scoring.mode=real",
        "app.scoring.checks.actor-overlap.mode=real",
        "app.scoring.checks.fresh-accounts.mode=real",
        "app.scoring.checks.engagement.mode=real"})
class PerfSmokeTest {

    private static final Instant FROM = Instant.parse("2026-11-11T00:00:00Z");
    private static final TimeWindow WINDOW = new TimeWindow(FROM, FROM.plus(Duration.ofHours(6)), 6);
    private static final RepoRef HOT = new RepoRef("hot", "repo");
    private static final int HOURS = 6;
    private static final int STARS_PER_HOUR = 50_000;
    private static final int REPOS = 40_000;
    private static final int ACTORS = 400_000;
    private static final int REQUESTS = 200;
    private static final int WARMUP = 30;
    private static final long P95_SLO_MS = 100;

    @Autowired
    private StatsAggregator aggregator;

    @Autowired
    private ScoringEngine scoringEngine;

    @Autowired
    private StarTimelineService timelineService;

    @Test
    void indexBuildAndRequestPathStayWithinBudget() throws IOException {
        Random random = new Random(42);

        // 1. индекс звёзд за 6 часов + горячий репозиторий с фермой из 300 аккаунтов
        long heapBefore = usedHeapAfterGc();
        long buildStarted = System.nanoTime();
        List<StarIndex> hours = new ArrayList<>(HOURS);
        List<GhEvent> hotEvents = new ArrayList<>();
        for (int hour = 0; hour < HOURS; hour++) {
            StarIndex.Builder builder = new StarIndex.Builder(1_000_000);
            Instant hourStart = FROM.plus(Duration.ofHours(hour));
            for (int i = 0; i < STARS_PER_HOUR; i++) {
                builder.add("r/" + skewed(random, REPOS), random.nextInt(ACTORS));
            }
            for (int bot = 0; bot < 300; bot++) {
                builder.add("farm/target-" + (bot % 5), 9_000_000 + bot);
            }
            for (int i = 0; i < 500; i++) {
                long actor = hour == 3 && i < 300 ? 9_000_000 + i : random.nextInt(ACTORS);
                Instant at = hourStart.plusSeconds(hour == 3 && i < 300 ? 600 + i : random.nextInt(3600));
                builder.add(HOT.fullName(), actor);
                hotEvents.add(new GhEvent("w" + hour + "-" + i, EventType.WATCH, 1, HOT.fullName(), actor,
                        "u" + actor, at, at.minus(Duration.ofDays(actor >= 9_000_000 ? 3 : 900))));
            }
            for (int i = 0; i < 40; i++) {
                Instant at = hourStart.plusSeconds(random.nextInt(3600));
                hotEvents.add(new GhEvent("p" + hour + "-" + i, i % 4 == 0 ? EventType.FORK : EventType.PUSH, 1,
                        HOT.fullName(), 100 + i, "dev" + i, at, null));
            }
            hours.add(builder.build(1));
        }
        StarIndex index = StarIndex.merge(hours);
        long buildMs = (System.nanoTime() - buildStarted) / 1_000_000;
        long heapAfter = usedHeapAfterGc();
        long indexHeapMb = Math.max(0, heapAfter - heapBefore) / (1024 * 1024);

        // 2. путь запроса по горячему репозиторию
        List<StarMark> marks = hotEvents.stream()
                .filter(event -> event.type() == EventType.WATCH)
                .map(event -> new StarMark(event.createdAt(), event.actorId(), false))
                .toList();
        long[] latencies = new long[REQUESTS];
        RepoScore last = null;
        for (int i = 0; i < WARMUP + REQUESTS; i++) {
            long started = System.nanoTime();
            RepoAggregate aggregate = aggregator.aggregate(HOT, WINDOW, hotEvents, index);
            last = scoringEngine.score(aggregate.snapshot());
            timelineService.build(aggregate.snapshot(), last, marks);
            if (i >= WARMUP) {
                latencies[i - WARMUP] = System.nanoTime() - started;
            }
        }
        Arrays.sort(latencies);
        double p50 = latencies[REQUESTS / 2] / 1e6;
        double p95 = latencies[(int) (REQUESTS * 0.95)] / 1e6;
        double max = latencies[REQUESTS - 1] / 1e6;

        writeReport(index, buildMs, indexHeapMb, p50, p95, max, last);

        assertThat(index.pairCount()).isGreaterThan(250_000);
        assertThat(last.checks()).anySatisfy(check -> {
            assertThat(check.checkId()).isEqualTo("actor_overlap");
            assertThat(check.active()).isTrue();
        });
        // с запасом против шумных раннеров; SLO и фактические цифры — в отчёте
        assertThat(buildMs).as("index build, ms").isLessThan(15_000);
        assertThat(indexHeapMb).as("star index heap, MB").isLessThan(512);
        assertThat(p95).as("request p95, ms").isLessThan(P95_SLO_MS * 3);
    }

    /** Распределение популярности: немногие репозитории получают большую часть звёзд. */
    private static int skewed(Random random, int bound) {
        double x = random.nextDouble();
        return (int) (bound * x * x * x);
    }

    private static long usedHeapAfterGc() {
        Runtime runtime = Runtime.getRuntime();
        for (int i = 0; i < 3; i++) {
            System.gc();
        }
        return runtime.totalMemory() - runtime.freeMemory();
    }

    private static void writeReport(StarIndex index, long buildMs, long heapMb,
                                    double p50, double p95, double max, RepoScore score) throws IOException {
        Path dir = Path.of("target", "perf-smoke");
        Files.createDirectories(dir);
        String report = String.format(Locale.ROOT, """
                # Star Guard perf-smoke

                Синтетика: %d ч × %d звёзд, %d репозиториев, %d акторов; горячий репозиторий — %s.
                JVM: %s, max heap %d MB.

                | Метрика | Значение | Цель |
                |---|---|---|
                | Индекс звёзд C4: пар | %d | — |
                | Индекс звёзд C4: репозиториев | %d | — |
                | Сборка индекса звёзд, мс | %d | — |
                | Heap индекса звёзд, МБ | %d | бюджет индекса 1024 |
                | Запрос (агрегаты + score + таймлайн), p50, мс | %.1f | — |
                | Запрос, p95, мс | %.1f | < %d |
                | Запрос, max, мс | %.1f | — |
                | Итоговый score горячего репозитория | %d (%s) | — |
                """,
                HOURS, STARS_PER_HOUR, REPOS, ACTORS, HOT.fullName(),
                Runtime.version(), Runtime.getRuntime().maxMemory() / (1024 * 1024),
                index.pairCount(), index.repoCount(), buildMs, heapMb, p50, p95, P95_SLO_MS, max,
                score.score(), score.level());
        Files.writeString(dir.resolve("report.md"), report);
        System.out.println(report);
    }
}
