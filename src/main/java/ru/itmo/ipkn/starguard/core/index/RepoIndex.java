package ru.itmo.ipkn.starguard.core.index;

import ru.itmo.ipkn.starguard.core.archive.GhArchiveParser;
import ru.itmo.ipkn.starguard.core.domain.DataSource;
import ru.itmo.ipkn.starguard.core.domain.RepoRef;

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

    /**
     * Переносит репозиторий из long-tail в топ текущего снимка (F4-2). Подмена атомарная:
     * если параллельно пришёл новый снимок, перенос повторяется уже на нём.
     *
     * @return true — репозиторий в топе (был или перенесён); false — снимка нет
     *         или у репозитория нет событий в окне
     */
    public boolean promote(RepoRef repo) {
        String key = GhArchiveParser.normalizeFullName(repo.fullName());
        while (true) {
            RepoIndexSnapshot snapshot = current.get();
            if (snapshot == null) {
                return false;
            }
            Optional<IndexHit> hit = snapshot.lookup(repo);
            if (hit.isEmpty()) {
                return false;
            }
            if (hit.get().tier() == DataSource.Tier.TOP) {
                return true;
            }
            if (current.compareAndSet(snapshot, snapshot.withRepo(key, hit.get().repo()))) {
                return true;
            }
        }
    }
}
