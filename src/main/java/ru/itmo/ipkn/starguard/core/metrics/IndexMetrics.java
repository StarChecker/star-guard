package ru.itmo.ipkn.starguard.core.metrics;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import ru.itmo.ipkn.starguard.core.domain.StarIndex;
import ru.itmo.ipkn.starguard.core.index.RepoIndex;
import ru.itmo.ipkn.starguard.core.index.RepoIndexSnapshot;

import java.time.Clock;
import java.time.Duration;
import java.util.function.ToDoubleFunction;

/**
 * Gauges состояния индекса (X-2). Читают текущий snapshot при каждом скрейпе;
 * пока индекса нет (отключён или строится) — NaN.
 */
@Component
public class IndexMetrics {

    public IndexMetrics(
            MeterRegistry registry,
            ObjectProvider<RepoIndex> repoIndex,
            ObjectProvider<IndexMemoryMonitor> memoryMonitor,
            Clock clock) {
        gauge(registry, StarGuardMetrics.INDEX_SIZE, "Репозиториев в индексе", repoIndex,
                snapshot -> snapshot.repos().size());
        gauge(registry, StarGuardMetrics.INDEX_HOURS, "Часов окна в индексе", repoIndex,
                RepoIndexSnapshot::hoursLoaded);
        gauge(registry, StarGuardMetrics.INDEX_STAR_PAIRS, "Пар (репозиторий, актор) в индексе звёзд C4", repoIndex,
                snapshot -> snapshot.starIndexByHour().values().stream().mapToLong(StarIndex::pairCount).sum());
        gauge(registry, StarGuardMetrics.INDEX_AGE, "Возраст индекса, секунды", repoIndex,
                snapshot -> Duration.between(snapshot.builtAt(), clock.instant()).toSeconds());

        Gauge.builder(StarGuardMetrics.INDEX_MEMORY, memoryMonitor,
                        provider -> monitorValue(provider, IndexMemoryMonitor::lastHeapUsed))
                .description("Heap сразу после сборки индекса")
                .baseUnit("bytes")
                .register(registry);
        Gauge.builder(StarGuardMetrics.INDEX_MEMORY_BUDGET, memoryMonitor,
                        provider -> monitorValue(provider, IndexMemoryMonitor::budgetBytes))
                .description("Бюджет памяти индекса")
                .baseUnit("bytes")
                .register(registry);
        Gauge.builder(StarGuardMetrics.INDEX_MEMORY_OVER_BUDGET, memoryMonitor,
                        provider -> monitorValue(provider, IndexMemoryMonitor::overBudget))
                .description("1 — последняя сборка индекса вышла за бюджет памяти")
                .register(registry);
    }

    private static void gauge(MeterRegistry registry, String name, String description,
                              ObjectProvider<RepoIndex> repoIndex, ToDoubleFunction<RepoIndexSnapshot> value) {
        Gauge.builder(name, repoIndex, provider -> {
                    RepoIndex index = provider.getIfAvailable();
                    return index == null ? Double.NaN
                            : index.snapshot().map(value::applyAsDouble).orElse(Double.NaN);
                })
                .description(description)
                .register(registry);
    }

    private static double monitorValue(ObjectProvider<IndexMemoryMonitor> provider,
                                       ToDoubleFunction<IndexMemoryMonitor> value) {
        IndexMemoryMonitor monitor = provider.getIfAvailable();
        if (monitor == null) {
            return Double.NaN;
        }
        double result = value.applyAsDouble(monitor);
        return result < 0 ? Double.NaN : result;
    }
}
