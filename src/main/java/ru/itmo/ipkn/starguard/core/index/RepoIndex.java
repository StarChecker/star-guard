package ru.itmo.ipkn.starguard.core.index;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/** Холдер актуального снимка индекса; подмена атомарная. */
public class RepoIndex {

    private final AtomicReference<RepoIndexSnapshot> current = new AtomicReference<>();

    public Optional<RepoIndexSnapshot> snapshot() {
        return Optional.ofNullable(current.get());
    }

    public boolean isReady() {
        return current.get() != null;
    }

    public void swap(RepoIndexSnapshot snapshot) {
        current.set(snapshot);
    }
}
