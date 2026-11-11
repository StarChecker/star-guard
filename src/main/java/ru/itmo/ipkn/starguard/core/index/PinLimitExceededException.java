package ru.itmo.ipkn.starguard.core.index;

/** Реестр закреплённых репозиториев заполнен; код ответа API — {@code PIN_LIMIT_REACHED}. */
public class PinLimitExceededException extends RuntimeException {

    public PinLimitExceededException(int maxPinned) {
        super(("Pinned repositories limit reached (%d). Increase app.index.pins.max-pinned "
                + "or wait for pins to expire (F4-3).").formatted(maxPinned));
    }
}
