package ru.itmo.ipkn.starguard.core.archive;

import ru.itmo.ipkn.starguard.core.domain.RepoRef;
import ru.itmo.ipkn.starguard.core.domain.TimeWindow;

/** Источник событий репозитория за окно. */
public interface GhArchiveClient {

    ArchiveFetchResult fetchEvents(RepoRef repo, TimeWindow window);
}
