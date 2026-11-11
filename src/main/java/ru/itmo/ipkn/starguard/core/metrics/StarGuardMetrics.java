package ru.itmo.ipkn.starguard.core.metrics;

/**
 * Имена метрик Star Guard (X-2). В Prometheus точки заменяются на подчёркивания,
 * таймеры получают суффиксы {@code _seconds_count/_sum/_max}.
 */
public final class StarGuardMetrics {

    /** Таймер сборки индекса; тег {@code outcome = success | failure}. */
    public static final String INDEX_BUILD_DURATION = "starguard.index.build.duration";
    /** Сколько репозиториев в индексе. */
    public static final String INDEX_SIZE = "starguard.index.size";
    /** Сколько часов окна загружено в индекс. */
    public static final String INDEX_HOURS = "starguard.index.hours.loaded";
    /** Пар (репозиторий, актор) в индексе звёзд для C4. */
    public static final String INDEX_STAR_PAIRS = "starguard.index.star.pairs";
    /** Возраст текущего индекса, секунды. */
    public static final String INDEX_AGE = "starguard.index.age";
    /** Занятый heap сразу после сборки и подмены индекса, байты. */
    public static final String INDEX_MEMORY = "starguard.index.memory";
    /** Бюджет памяти индекса, байты ({@code app.index.memory-budget-mb}). */
    public static final String INDEX_MEMORY_BUDGET = "starguard.index.memory.budget";
    /** 1 — последняя сборка вышла за бюджет памяти. */
    public static final String INDEX_MEMORY_OVER_BUDGET = "starguard.index.memory.over.budget";
    /** Таймер скана по запросу; теги {@code source = github | archive}, {@code outcome}. */
    public static final String SCAN_LATENCY = "starguard.scan.latency";
    /** Сколько сканов идёт сейчас. */
    public static final String SCAN_ACTIVE = "starguard.scan.active";

    private StarGuardMetrics() {
    }
}
