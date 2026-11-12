package ru.itmo.ipkn.starguard.core.metrics;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import ru.itmo.ipkn.starguard.core.archive.EventFilter;
import ru.itmo.ipkn.starguard.core.archive.GhArchiveBulkReader;
import ru.itmo.ipkn.starguard.core.archive.HourReadResult;
import ru.itmo.ipkn.starguard.core.archive.TransientArchiveException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import ru.itmo.ipkn.starguard.core.config.IndexMemoryProperties;
import ru.itmo.ipkn.starguard.core.domain.StarIndex;
import ru.itmo.ipkn.starguard.core.domain.TimeWindow;
import ru.itmo.ipkn.starguard.core.index.RepoIndex;
import ru.itmo.ipkn.starguard.core.index.RepoIndexSnapshot;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MetricsTest {

    private static final Instant NOW = Instant.parse("2026-11-11T12:00:00Z");

    @Test
    void memoryMonitorFlagsBudgetOverrun() {
        IndexMemoryMonitor monitor = new IndexMemoryMonitor(new IndexMemoryProperties(100), () -> 150L * 1024 * 1024);

        assertThat(monitor.afterSwap(1000)).isTrue();
        assertThat(monitor.overBudget()).isEqualTo(1);
        assertThat(monitor.lastHeapUsed()).isEqualTo(150L * 1024 * 1024);
        assertThat(monitor.budgetBytes()).isEqualTo(100L * 1024 * 1024);

        IndexMemoryMonitor fits = new IndexMemoryMonitor(new IndexMemoryProperties(100), () -> 50L * 1024 * 1024);
        assertThat(fits.afterSwap(10)).isFalse();
        assertThat(fits.overBudget()).isZero();
    }

    @Test
    void memoryBudgetHasSaneDefaultAndValidation() {
        assertThat(IndexMemoryProperties.defaults().memoryBudgetMb()).isEqualTo(1024);
        assertThatThrownBy(() -> new IndexMemoryProperties(10)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void indexGaugesReadCurrentSnapshot() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        DefaultListableBeanFactory beans = new DefaultListableBeanFactory();
        RepoIndex index = new RepoIndex();
        IndexMemoryMonitor monitor = new IndexMemoryMonitor(new IndexMemoryProperties(1024), () -> 300L * 1024 * 1024);
        beans.registerSingleton("repoIndex", index);
        beans.registerSingleton("memoryMonitor", monitor);
        new IndexMetrics(registry, beans.getBeanProvider(RepoIndex.class),
                beans.getBeanProvider(IndexMemoryMonitor.class), Clock.fixed(NOW, ZoneOffset.UTC));

        assertThat(registry.get(StarGuardMetrics.INDEX_SIZE).gauge().value()).isNaN();
        assertThat(registry.get(StarGuardMetrics.INDEX_MEMORY).gauge().value()).isNaN();

        StarIndex.Builder stars = new StarIndex.Builder(100);
        stars.add("a/one", 1);
        stars.add("a/one", 2);
        stars.add("b/two", 1);
        TimeWindow window = new TimeWindow(NOW.minus(Duration.ofHours(1)), NOW, 1);
        index.swap(new RepoIndexSnapshot(window, NOW.minus(Duration.ofMinutes(5)), 1, List.of(), 0,
                Map.of(), Map.of(window.from(), stars.build(1))));
        monitor.afterSwap(0);

        assertThat(registry.get(StarGuardMetrics.INDEX_SIZE).gauge().value()).isZero();
        assertThat(registry.get(StarGuardMetrics.INDEX_HOURS).gauge().value()).isEqualTo(1);
        assertThat(registry.get(StarGuardMetrics.INDEX_STAR_PAIRS).gauge().value()).isEqualTo(3);
        assertThat(registry.get(StarGuardMetrics.INDEX_AGE).gauge().value()).isEqualTo(300);
        assertThat(registry.get(StarGuardMetrics.INDEX_MEMORY).gauge().value()).isEqualTo(300.0 * 1024 * 1024);
        assertThat(registry.get(StarGuardMetrics.INDEX_MEMORY_OVER_BUDGET).gauge().value()).isZero();
    }

    @Test
    void parseMetricsCountLinesHoursAndRate() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        ParseMetrics metrics = new ParseMetrics(registry);

        metrics.hourRead(ParseMetrics.SOURCE_INDEX, new HourReadResult(NOW, 3, false, false,
                2_000_000, 900_000_000, 4_000_000_000L));
        metrics.hourRead(ParseMetrics.SOURCE_INDEX, new HourReadResult(NOW.plusSeconds(3600), 0, true, false));
        metrics.attemptFailed(ParseMetrics.SOURCE_INDEX);

        assertThat(registry.get(StarGuardMetrics.PARSE_LINES).tag("source", "index").counter().count())
                .isEqualTo(2_000_000);
        assertThat(registry.get(StarGuardMetrics.PARSE_BYTES).counter().count()).isEqualTo(900_000_000);
        assertThat(registry.get(StarGuardMetrics.PARSE_ERRORS).counter().count()).isEqualTo(3);
        assertThat(registry.get(StarGuardMetrics.PARSE_HOURS).tag("outcome", "parsed").counter().count()).isEqualTo(1);
        assertThat(registry.get(StarGuardMetrics.PARSE_HOURS).tag("outcome", "missing").counter().count()).isEqualTo(1);
        assertThat(registry.get(StarGuardMetrics.PARSE_HOURS).tag("outcome", "error").counter().count()).isEqualTo(1);
        assertThat(registry.get(StarGuardMetrics.PARSE_HOUR_DURATION).timer().count()).isEqualTo(1);
        assertThat(registry.get(StarGuardMetrics.PARSE_LINES_PER_SECOND).tag("source", "index").gauge().value())
                .isEqualTo(500_000);
    }

    @Test
    void meteredBulkReaderRecordsResultsAndFailedAttempts() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        ParseMetrics metrics = new ParseMetrics(registry);
        GhArchiveBulkReader flaky = new GhArchiveBulkReader() {
            private int calls;

            @Override
            public HourReadResult readHour(Instant hour, java.util.function.Consumer<ru.itmo.ipkn.starguard.core.domain.GhEvent> sink) {
                if (calls++ == 0) {
                    throw new TransientArchiveException("boom");
                }
                return new HourReadResult(hour, 0, false, false, 10, 1000, 1_000_000);
            }
        };
        GhArchiveBulkReader reader = new MeteredGhArchiveBulkReader(flaky, metrics);

        assertThatThrownBy(() -> reader.readHour(NOW, EventFilter.ALL, event -> { }))
                .isInstanceOf(TransientArchiveException.class);
        HourReadResult result = reader.readHour(NOW, EventFilter.ALL, event -> { });

        assertThat(result.lines()).isEqualTo(10);
        assertThat(registry.get(StarGuardMetrics.PARSE_HOURS).tag("outcome", "error").counter().count()).isEqualTo(1);
        assertThat(registry.get(StarGuardMetrics.PARSE_HOURS).tag("outcome", "parsed").counter().count()).isEqualTo(1);
        assertThat(registry.get(StarGuardMetrics.PARSE_LINES).tag("source", "index").counter().count()).isEqualTo(10);
    }
}
