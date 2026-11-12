package ru.itmo.ipkn.starguard.core.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;
import ru.itmo.ipkn.starguard.core.archive.HourReadResult;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Метрики разбора GH Archive (X-2): строки, байты, ошибки, часы и скорость разбора.
 * Источник {@code index} — сборка индекса топ-N, {@code scan} — запрос одного репозитория.
 */
@Component
public class ParseMetrics {

    public static final String SOURCE_INDEX = "index";
    public static final String SOURCE_SCAN = "scan";

    private final MeterRegistry registry;
    private final Map<String, AtomicLong> lastRate = new ConcurrentHashMap<>();

    public ParseMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    /** Учитывает результат чтения часа: прочитанный, отсутствующий в архиве или упавший. */
    public void hourRead(String source, HourReadResult result) {
        if (result.failed()) {
            hour(source, "failed");
            return;
        }
        if (result.missing()) {
            hour(source, "missing");
            return;
        }
        hour(source, "parsed");
        counter(StarGuardMetrics.PARSE_LINES, source, "Строк GH Archive прочитано", null).increment(result.lines());
        counter(StarGuardMetrics.PARSE_BYTES, source, "Распакованных байт GH Archive", "bytes").increment(result.bytes());
        counter(StarGuardMetrics.PARSE_ERRORS, source, "Строк GH Archive, которые не разобрались", null)
                .increment(result.parseErrors());
        if (result.parseNanos() > 0) {
            Timer.builder(StarGuardMetrics.PARSE_HOUR_DURATION)
                    .description("Чтение и разбор одного часового дампа")
                    .tag("source", source)
                    .register(registry)
                    .record(result.parseNanos(), TimeUnit.NANOSECONDS);
            double perSecond = result.lines() * 1e9 / result.parseNanos();
            rate(source).set(Double.doubleToLongBits(perSecond));
        }
    }

    /** Попытка чтения часа упала (сеть, 5xx); вызывающий обычно повторит её. */
    public void attemptFailed(String source) {
        hour(source, "error");
    }

    private void hour(String source, String outcome) {
        Counter.builder(StarGuardMetrics.PARSE_HOURS)
                .description("Часовых дампов GH Archive обработано")
                .tag("source", source)
                .tag("outcome", outcome)
                .register(registry)
                .increment();
    }

    private Counter counter(String name, String source, String description, String unit) {
        Counter.Builder builder = Counter.builder(name).description(description).tag("source", source);
        if (unit != null) {
            builder.baseUnit(unit);
        }
        return builder.register(registry);
    }

    private AtomicLong rate(String source) {
        return lastRate.computeIfAbsent(source, key -> {
            AtomicLong bits = new AtomicLong(Double.doubleToLongBits(Double.NaN));
            Gauge.builder(StarGuardMetrics.PARSE_LINES_PER_SECOND, bits, value -> Double.longBitsToDouble(value.get()))
                    .description("Скорость разбора последнего прочитанного часа, строк в секунду")
                    .tag("source", key)
                    .register(registry);
            return bits;
        });
    }
}
