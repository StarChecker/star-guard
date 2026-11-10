package ru.itmo.ipkn.starguard.core.scoring;

public class InvalidMockModeException extends RuntimeException {

    public InvalidMockModeException(String value) {
        super("Unknown mock_mode '" + value + "'. Allowed: healthy, borderline, suspicious");
    }
}
