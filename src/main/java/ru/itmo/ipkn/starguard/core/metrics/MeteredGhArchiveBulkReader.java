package ru.itmo.ipkn.starguard.core.metrics;

import ru.itmo.ipkn.starguard.core.archive.EventFilter;
import ru.itmo.ipkn.starguard.core.archive.GhArchiveBulkReader;
import ru.itmo.ipkn.starguard.core.archive.HourReadResult;
import ru.itmo.ipkn.starguard.core.domain.GhEvent;

import java.time.Instant;
import java.util.function.Consumer;

/** Обёртка над чтением часа для индекса: каждый результат — в {@link ParseMetrics} (X-2). */
public final class MeteredGhArchiveBulkReader implements GhArchiveBulkReader {

    private final GhArchiveBulkReader delegate;
    private final ParseMetrics metrics;

    public MeteredGhArchiveBulkReader(GhArchiveBulkReader delegate, ParseMetrics metrics) {
        this.delegate = delegate;
        this.metrics = metrics;
    }

    @Override
    public HourReadResult readHour(Instant hour, Consumer<GhEvent> sink) {
        try {
            return recorded(delegate.readHour(hour, sink));
        } catch (RuntimeException e) {
            metrics.attemptFailed(ParseMetrics.SOURCE_INDEX);
            throw e;
        }
    }

    @Override
    public HourReadResult readHour(Instant hour, EventFilter filter, Consumer<GhEvent> sink) {
        try {
            return recorded(delegate.readHour(hour, filter, sink));
        } catch (RuntimeException e) {
            metrics.attemptFailed(ParseMetrics.SOURCE_INDEX);
            throw e;
        }
    }

    private HourReadResult recorded(HourReadResult result) {
        metrics.hourRead(ParseMetrics.SOURCE_INDEX, result);
        return result;
    }
}
