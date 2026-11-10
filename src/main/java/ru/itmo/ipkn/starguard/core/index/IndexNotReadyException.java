package ru.itmo.ipkn.starguard.core.index;

/** Индекс ещё не построен либо отключён. */
public class IndexNotReadyException extends RuntimeException {

    public IndexNotReadyException() {
        super("Repository index is not ready or disabled");
    }

    public IndexNotReadyException(String message) {
        super(message);
    }
}
