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
    /** Строк GH Archive прочитано; тег {@code source = index | scan}. {@code rate()} даёт строки в секунду. */
    public static final String PARSE_LINES = "starguard.parse.lines";
    /** Распакованных байт в этих строках; тег {@code source}. */
    public static final String PARSE_BYTES = "starguard.parse.bytes";
    /** Строк, которые не разобрались (битый JSON, слишком длинная строка); тег {@code source}. */
    public static final String PARSE_ERRORS = "starguard.parse.errors";
    /**
     * Попыток чтения часовых дампов; теги {@code source} и {@code outcome}: {@code parsed}, {@code missing}
     * (часа нет в архиве), {@code failed} (час пропущен), {@code error} (попытка упала, будет повтор).
     */
    public static final String PARSE_HOURS = "starguard.parse.hours";
    /** Таймер чтения и разбора одного часового дампа; тег {@code source}. */
    public static final String PARSE_HOUR_DURATION = "starguard.parse.hour.duration";
    /** Скорость разбора последнего прочитанного часа, строк в секунду; тег {@code source}. */
    public static final String PARSE_LINES_PER_SECOND = "starguard.parse.lines.per.second";

    private StarGuardMetrics() {
    }
}
