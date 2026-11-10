package ru.itmo.ipkn.starguard.core.scan;

/** Скан с таким id не найден или уже удалён по TTL. */
public class ScanNotFoundException extends RuntimeException {

    public ScanNotFoundException(String id) {
        super("Scan " + id + " not found or expired");
    }
}
