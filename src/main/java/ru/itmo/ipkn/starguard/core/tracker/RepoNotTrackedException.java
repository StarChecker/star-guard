package ru.itmo.ipkn.starguard.core.tracker;

import ru.itmo.ipkn.starguard.core.domain.RepoRef;

public class RepoNotTrackedException extends RuntimeException {

    public RepoNotTrackedException(RepoRef repo) {
        super("Repository " + repo.fullName() + " is not tracked; PUT /api/v1/tracker/repos/"
                + repo.fullName() + " starts tracking it");
    }
}
