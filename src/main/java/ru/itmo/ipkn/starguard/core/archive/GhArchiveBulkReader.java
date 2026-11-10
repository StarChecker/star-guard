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
}
