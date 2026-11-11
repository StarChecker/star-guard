package ru.itmo.ipkn.starguard.core.github;

import ru.itmo.ipkn.starguard.core.domain.RepoRef;

/** Репозитория нет на GitHub: удалён, скрыт или переименован без редиректа. */
public class RepoGoneException extends RuntimeException {

    public RepoGoneException(RepoRef repo) {
        super("Repository " + repo.fullName() + " is not available on GitHub (deleted or private)");
    }
}
