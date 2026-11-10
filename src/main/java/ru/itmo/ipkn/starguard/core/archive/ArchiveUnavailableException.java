package ru.itmo.ipkn.starguard.core.archive;

public class ArchiveUnavailableException extends RuntimeException {

    public ArchiveUnavailableException(String message) {
        super(message);
    }

    public ArchiveUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
