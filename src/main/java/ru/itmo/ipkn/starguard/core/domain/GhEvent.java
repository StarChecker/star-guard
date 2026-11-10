package ru.itmo.ipkn.starguard.core.domain;

import java.time.Duration;
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
        Instant createdAt,
        Instant actorCreatedAt
) {

    public GhEvent {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(repoFullName, "repoFullName");
        Objects.requireNonNull(createdAt, "createdAt");
        actorLogin = actorLogin == null ? "" : actorLogin;
    }

    public GhEvent(
            String eventId,
            EventType type,
            long repoId,
            String repoFullName,
            long actorId,
            String actorLogin,
            Instant createdAt) {
        this(eventId, type, repoId, repoFullName, actorId, actorLogin, createdAt, null);
    }

    public GhEvent shiftedBy(Duration shift) {
        return new GhEvent(eventId, type, repoId, repoFullName, actorId, actorLogin,
                createdAt.plus(shift),
                actorCreatedAt == null ? null : actorCreatedAt.plus(shift));
    }
}
