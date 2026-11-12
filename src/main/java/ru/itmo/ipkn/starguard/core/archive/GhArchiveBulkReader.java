package ru.itmo.ipkn.starguard.core.archive;

import ru.itmo.ipkn.starguard.core.domain.GhEvent;

import java.time.Instant;
import java.util.function.Consumer;

/**
 * Потоковое чтение одного часа GH Archive для всех репозиториев.
 * В отличие от {@link GhArchiveClient}, не фильтрует по репозиторию:
 * вызывающий сам решает, что делать с каждым событием.
 */
public interface GhArchiveBulkReader {

    HourReadResult readHour(Instant hour, Consumer<GhEvent> sink);

    /**
     * Чтение часа с ранним фильтром: строки, которые {@code filter} отклонил по
     * {@code type} или {@code repo.name}, не дочитываются. Реализация по умолчанию
     * фильтрует уже разобранные события — результат тот же, только без ускорения.
     */
    default HourReadResult readHour(Instant hour, EventFilter filter, Consumer<GhEvent> sink) {
        return readHour(hour, event -> {
            if (filter.accepts(event)) {
                sink.accept(event);
            }
        });
    }
}
