package ru.itmo.ipkn.starguard.core.archive;

/** Временная ошибка загрузки часа GH Archive: IO или 5xx; можно повторить. */
public class TransientArchiveException extends RuntimeException {

    public TransientArchiveException(String message) {
        super(message);
    }

    public TransientArchiveException(String message, Throwable cause) {
        super(message, cause);
    }
}
