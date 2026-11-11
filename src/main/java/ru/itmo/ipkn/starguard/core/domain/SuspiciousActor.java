package ru.itmo.ipkn.starguard.core.domain;

import java.time.Instant;
import java.util.List;

/** Публичная выборка: без сырого actorId; login отсутствует в hash-режиме и без профиля. */
public record SuspiciousActor(
        String actorHash,
        String login,
        List<String> reasons,
        Instant accountCreatedAt,
        Instant firstSeen,
        int sharedRepos
) {
    public SuspiciousActor {
        reasons = List.copyOf(reasons);
    }
}
