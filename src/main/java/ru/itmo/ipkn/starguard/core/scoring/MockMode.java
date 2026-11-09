package ru.itmo.ipkn.starguard.core.scoring;

import java.util.Locale;

/** PoC-профиль мок-проверок. В MVP будет удалён вместе с моками. */
public enum MockMode {
    HEALTHY,
    BORDERLINE,
    SUSPICIOUS;

    public static MockMode fromNullable(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new InvalidMockModeException(value);
        }
    }

    public String slug() {
        return name().toLowerCase(Locale.ROOT);
    }
}
