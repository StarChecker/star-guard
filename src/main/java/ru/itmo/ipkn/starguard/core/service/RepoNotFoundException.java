package ru.itmo.ipkn.starguard.core.service;

import ru.itmo.ipkn.starguard.core.domain.RepoRef;
import ru.itmo.ipkn.starguard.core.domain.TimeWindow;

public class RepoNotFoundException extends RuntimeException {

    private final RepoRef repo;
    private final TimeWindow window;

    public RepoNotFoundException(RepoRef repo, TimeWindow window) {
        super("No GH Archive events for %s in the requested window [%s, %s)"
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
