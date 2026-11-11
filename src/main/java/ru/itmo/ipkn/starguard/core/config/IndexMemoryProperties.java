package ru.itmo.ipkn.starguard.core.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Бюджет памяти индекса (F3-6). Ключ — {@code app.index.memory-budget-mb}, как в плане F3;
 * отдельная запись, чтобы не конфликтовать с {@link RepoIndexProperties}.
 *
 * @param memoryBudgetMb сколько heap можно занять после сборки индекса; JVM запускается с {@code -Xmx1280m}
 */
@ConfigurationProperties(prefix = "app.index")
public record IndexMemoryProperties(Integer memoryBudgetMb) {

    public IndexMemoryProperties {
        memoryBudgetMb = memoryBudgetMb == null ? 1024 : memoryBudgetMb;
        if (memoryBudgetMb < 64) {
            throw new IllegalArgumentException("app.index.memory-budget-mb must be >= 64");
        }
    }

    public long budgetBytes() {
        return memoryBudgetMb * 1024L * 1024L;
    }

    public static IndexMemoryProperties defaults() {
        return new IndexMemoryProperties(null);
    }
}
