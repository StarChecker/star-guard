package ru.itmo.ipkn.starguard.core.github;

import ru.itmo.ipkn.starguard.core.domain.RepoRef;

public class StarsHiddenException extends RuntimeException {

    public static final String CODE = "STARS_HIDDEN";

    private final long stargazerCount;

    public StarsHiddenException(RepoRef repo, long stargazerCount) {
        super("GitHub hides the stargazer list of " + repo.fullName() + " (" + stargazerCount
                + " stars): since 2026-06-30 it is visible only to repository admins and collaborators");
        this.stargazerCount = stargazerCount;
    }

    public long stargazerCount() {
        return stargazerCount;
    }
}
