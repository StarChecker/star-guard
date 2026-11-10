package ru.itmo.ipkn.starguard.core.index;

/** Сборка индекса невозможна (например, ни один час GH Archive не загрузился). */
public class IndexBuildException extends RuntimeException {

    public IndexBuildException(String message) {
        super(message);
    }

    public IndexBuildException(String message, Throwable cause) {
        super(message, cause);
    }
}
