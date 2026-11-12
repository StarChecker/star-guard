package ru.itmo.ipkn.starguard.core.metrics;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.itmo.ipkn.starguard.core.config.IndexMemoryProperties;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;

/**
 * Контроль бюджета памяти индекса (F3-6): после каждой подмены snapshot запоминает
 * занятый heap и сравнивает с {@code app.index.memory-budget-mb}. При превышении —
 * предупреждение в лог и метрика {@link StarGuardMetrics#INDEX_MEMORY_OVER_BUDGET}.
 * <p>
 * Вытеснение (eviction) появится вместе с tiered-моделью F3-3: в текущей модели
 * вытеснять по частям нечего, поэтому здесь — измерение и сигнал.
 */
public class IndexMemoryMonitor {

    private static final Logger log = LoggerFactory.getLogger(IndexMemoryMonitor.class);
    private static final MemoryMXBean MEMORY = ManagementFactory.getMemoryMXBean();

    private final IndexMemoryProperties properties;
    private final LongSupplier heapUsed;
    private final AtomicLong lastHeapUsed = new AtomicLong(-1);
    private final AtomicLong overBudget = new AtomicLong();

    public IndexMemoryMonitor(IndexMemoryProperties properties) {
        this(properties, () -> MEMORY.getHeapMemoryUsage().getUsed());
    }

    IndexMemoryMonitor(IndexMemoryProperties properties, LongSupplier heapUsed) {
        this.properties = properties;
        this.heapUsed = heapUsed;
    }

    /** Вызывается после подмены индекса. Возвращает true, если бюджет превышен. */
    public boolean afterSwap(int repos) {
        long used = heapUsed.getAsLong();
        lastHeapUsed.set(used);
        boolean exceeded = used > properties.budgetBytes();
        overBudget.set(exceeded ? 1 : 0);
        if (exceeded) {
            log.warn("Heap after index swap is {} MB, above budget {} MB (repos={}); "
                            + "reduce app.index.top-n or window-hours until eviction (F3-3) lands",
                    used / (1024 * 1024), properties.memoryBudgetMb(), repos);
        }
        return exceeded;
    }

    public long lastHeapUsed() {
        return lastHeapUsed.get();
    }

    public long budgetBytes() {
        return properties.budgetBytes();
    }

    public long overBudget() {
        return overBudget.get();
    }
}
