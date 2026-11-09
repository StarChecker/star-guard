package ru.itmo.ipkn.starguard.core.scoring;

import ru.itmo.ipkn.starguard.core.domain.RepoSnapshot;

/**
 * Одна проверка репозитория. Реализация обязана быть чистой функцией:
 * без состояния между вызовами, без обращения к внешним системам.
 */
public interface RepoCheck {

    String id();

    CheckResult evaluate(RepoSnapshot snapshot);
}
