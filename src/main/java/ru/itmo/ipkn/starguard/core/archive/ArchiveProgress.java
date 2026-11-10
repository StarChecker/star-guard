package ru.itmo.ipkn.starguard.core.archive;

/** Прогресс загрузки окна: сколько часовых дампов уже обработано. */
@FunctionalInterface
public interface ArchiveProgress {

    ArchiveProgress NONE = (done, total) -> { };

    void hourDone(int done, int total);
}
