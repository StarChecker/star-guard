package ru.itmo.ipkn.starguard.core.index;

import ru.itmo.ipkn.starguard.core.domain.RepoRef;

/**
 * Добавление репозитория в индекс (F4-2): закрепляет его и, если у репозитория есть
 * события в окне, сразу переносит из long-tail в топ текущего снимка.
 * Операция идемпотентна.
 */
public class IndexAdmission {

    /** Чем закончилось добавление. */
    public enum Outcome {
        /** репозиторий в топе снимка: отчёт можно отдать сразу */
        INDEXED,
        /** у репозитория нет событий в окне индекса: нужен фоновый скан */
        NOT_IN_WINDOW
    }

    private final RepoIndex repoIndex;
    private final PinnedRepos pins;

    public IndexAdmission(RepoIndex repoIndex, PinnedRepos pins) {
        this.repoIndex = repoIndex;
        this.pins = pins;
    }

    /**
     * @throws IndexNotReadyException     индекс ещё строится
     * @throws PinLimitExceededException  реестр закреплённых заполнен
     */
    public Outcome admit(RepoRef repo) {
        if (repoIndex.snapshot().isEmpty()) {
            throw new IndexNotReadyException("Repository index is still warming up; retry later");
        }
        pins.pin(repo);
        return repoIndex.promote(repo) ? Outcome.INDEXED : Outcome.NOT_IN_WINDOW;
    }
}
