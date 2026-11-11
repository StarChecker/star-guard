package ru.itmo.ipkn.starguard.core.domain;

import java.time.Instant;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public record ActorActivity(
        long actorId,
        String login,
        Instant firstSeen,
        Instant lastActivityAt,
        Instant accountCreatedAt,
        boolean starred
) {

    public ActorActivity {
        Objects.requireNonNull(firstSeen, "firstSeen");
        login = login == null ? "" : login;
    }

    public static ActorActivity of(GhEvent event) {
        boolean star = event.type() == EventType.WATCH;
        return new ActorActivity(
                event.actorId(),
                event.actorLogin(),
                event.createdAt(),
                star ? null : event.createdAt(),
                event.actorCreatedAt(),
                star);
    }

    public ActorActivity merge(ActorActivity other) {
        return new ActorActivity(
                actorId,
                login.isEmpty() ? other.login : login,
                firstSeen.isAfter(other.firstSeen) ? other.firstSeen : firstSeen,
                latest(lastActivityAt, other.lastActivityAt),
                accountCreatedAt != null ? accountCreatedAt : other.accountCreatedAt,
                starred || other.starred);
    }

    public ActorActivity withAccountCreatedAt(Instant createdAt) {
        return new ActorActivity(actorId, login, firstSeen, lastActivityAt, createdAt, starred);
    }

    public ActorProfile toProfile() {
        return new ActorProfile(ActorProfile.hashOf(actorId), firstSeen, accountCreatedAt, lastActivityAt);
    }

    public static List<ActorActivity> starredSorted(Collection<ActorActivity> actors) {
        return actors.stream()
                .filter(ActorActivity::starred)
                .sorted(Comparator.comparingLong(ActorActivity::actorId))
                .toList();
    }

    public static List<ActorProfile> profiles(Collection<ActorActivity> starActors) {
        return starActors.stream()
                .map(ActorActivity::toProfile)
                .sorted(Comparator.comparing(ActorProfile::actorHash))
                .toList();
    }

    public static List<ActorActivity> withAccountCreatedAt(
            List<ActorActivity> actors, Map<Long, Instant> createdAtById) {
        return actors.stream()
                .map(actor -> actor.accountCreatedAt() == null && createdAtById.containsKey(actor.actorId())
                        ? actor.withAccountCreatedAt(createdAtById.get(actor.actorId()))
                        : actor)
                .toList();
    }

    private static Instant latest(Instant left, Instant right) {
        if (left == null) {
            return right;
        }
        if (right == null) {
            return left;
        }
        return left.isAfter(right) ? left : right;
    }
}
