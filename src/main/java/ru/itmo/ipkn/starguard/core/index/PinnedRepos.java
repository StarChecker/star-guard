package ru.itmo.ipkn.starguard.core.index;

import ru.itmo.ipkn.starguard.core.archive.GhArchiveParser;
import ru.itmo.ipkn.starguard.core.domain.RepoRef;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Репозитории, закреплённые в топе индекса через {@code PUT/POST /api/v1/index/repos} (F4-2).
 * <p>
 * Закреплённый репозиторий попадает в топ при каждой сборке, пока у него есть события
 * в окне, — даже если по звёздам он в топ-N не проходит. Повторное закрепление ничего
 * не меняет. Размер реестра ограничен: эндпоинт открытый. Хранится в памяти; TTL и
 * приоритетный скан — задача F4-3.
 */
public class PinnedRepos {

    private final int maxPinned;
    private final Map<String, RepoRef> pins = new ConcurrentHashMap<>();

    public PinnedRepos(int maxPinned) {
        if (maxPinned < 0) {
            throw new IllegalArgumentException("maxPinned must be >= 0");
        }
        this.maxPinned = maxPinned;
    }

    /** Реестр без закреплений: сборка индекса без F4-2. */
    public static PinnedRepos none() {
        return new PinnedRepos(0);
    }

    /**
     * Закрепляет репозиторий; повторный вызов для того же репозитория (без учёта регистра)
     * ничего не меняет.
     *
     * @throws PinLimitExceededException реестр заполнен, а репозиторий ещё не закреплён
     */
    public synchronized void pin(RepoRef repo) {
        String key = GhArchiveParser.normalizeFullName(repo.fullName());
        if (pins.containsKey(key)) {
            return;
        }
        if (pins.size() >= maxPinned) {
            throw new PinLimitExceededException(maxPinned);
        }
        pins.put(key, repo);
    }

    public boolean isPinned(String fullName) {
        return pins.containsKey(GhArchiveParser.normalizeFullName(fullName));
    }

    /** Нормализованные имена закреплённых репозиториев. */
    public Set<String> keys() {
        return Set.copyOf(pins.keySet());
    }

    public int size() {
        return pins.size();
    }

    public int maxPinned() {
        return maxPinned;
    }
}
