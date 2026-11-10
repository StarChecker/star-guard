package ru.itmo.ipkn.starguard.core.archive;

import ru.itmo.ipkn.starguard.core.domain.RepoRef;
import ru.itmo.ipkn.starguard.core.domain.TimeWindow;

/** Источник событий репозитория за окно. */
public interface GhArchiveClient {

    ArchiveFetchResult fetchEvents(RepoRef repo, TimeWindow window);

    /**
     * То же с отчётом о прогрессе по часам (для скана по запросу из расширения).
     * Реализация по умолчанию сообщает только о завершении.
     */
    default ArchiveFetchResult fetchEvents(RepoRef repo, TimeWindow window, ArchiveProgress progress) {
        ArchiveFetchResult result = fetchEvents(repo, window);
        progress.hourDone(window.hours(), window.hours());
        return result;
    }
}
