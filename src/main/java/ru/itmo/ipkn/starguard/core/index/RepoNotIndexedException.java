package ru.itmo.ipkn.starguard.core.index;

import ru.itmo.ipkn.starguard.core.domain.RepoRef;
import ru.itmo.ipkn.starguard.core.domain.TimeWindow;

/**
 * У репозитория нет событий в окне индекса: его нет ни в топ-N, ни в long-tail.
 * Код ответа API — {@code REPO_NOT_INDEXED}.
 */
public class RepoNotIndexedException extends RuntimeException {

    private final RepoRef repo;
    private final TimeWindow window;

    public RepoNotIndexedException(RepoRef repo, TimeWindow window) {
        super(("Repository %s has no events in the indexed window [%s, %s): neither the top-N "
                + "nor the long tail has data for it. Use GET /api/v1/index/repos to see the top "
                + "or POST /api/v1/scans to scan GH Archive directly.")
                .formatted(repo.fullName(), window.from(), window.to()));
        this.repo = repo;
        this.window = window;
    }

    public RepoRef repo() {
        return repo;
    }

    public TimeWindow window() {
        return window;
    }
}
