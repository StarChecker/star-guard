package ru.itmo.ipkn.starguard.core.index;

import ru.itmo.ipkn.starguard.core.domain.RepoRef;
import ru.itmo.ipkn.starguard.core.domain.TimeWindow;

/** Репозиторий не попал в предвычисленный топ-N индекс. */
public class RepoNotIndexedException extends RuntimeException {

    private final RepoRef repo;
    private final TimeWindow window;

    public RepoNotIndexedException(RepoRef repo, TimeWindow window) {
        super(("Repository %s is not in the top-N index for window [%s, %s). "
                + "Use GET /api/v1/index/repos to see available repositories.")
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
