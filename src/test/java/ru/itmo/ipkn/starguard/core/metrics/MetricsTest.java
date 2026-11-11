package ru.itmo.ipkn.starguard.core.metrics;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
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
}
