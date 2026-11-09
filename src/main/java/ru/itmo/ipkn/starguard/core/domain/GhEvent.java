package ru.itmo.ipkn.starguard.core.domain;

import java.time.Instant;
import java.util.Objects;

/** Событие GH Archive, уже отфильтрованное по репозиторию. */
public record GhEvent(
        String eventId,
        EventType type,
        long repoId,
        String repoFullName,
        long actorId,
        String actorLogin,
        Instant createdAt
) {

    public GhEvent {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(repoFullName, "repoFullName");
        Objects.requireNonNull(createdAt, "createdAt");
        actorLogin = actorLogin == null ? "" : actorLogin;
    }

    public GhEvent withCreatedAt(Instant newCreatedAt) {
        return new GhEvent(eventId, type, repoId, repoFullName, actorId, actorLogin, newCreatedAt);
    }
}
