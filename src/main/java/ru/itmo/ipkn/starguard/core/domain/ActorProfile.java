package ru.itmo.ipkn.starguard.core.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Objects;

public record ActorProfile(
        String actorHash,
        Instant firstSeen,
        Instant accountCreatedAt,
        Instant lastActivityAt
) {

    private static final int HASH_HEX_LENGTH = 12;

    public ActorProfile {
        Objects.requireNonNull(actorHash, "actorHash");
        Objects.requireNonNull(firstSeen, "firstSeen");
    }

    public static String hashOf(long actorId) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(("gh-actor:" + actorId).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest).substring(0, HASH_HEX_LENGTH);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}
