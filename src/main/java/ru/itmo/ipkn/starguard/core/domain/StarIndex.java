package ru.itmo.ipkn.starguard.core.domain;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;

/**
 * Глобальный индекс звёзд за окно: какие акторы поставили звёзды каким репозиториям.
 * Нужен проверке C4 «Пересечение акторов» — по событиям одного репозитория её не посчитать.
 * <p>
 * Строится из {@code WatchEvent} всех репозиториев часового дампа GH Archive. Хранится
 * компактно: имя репозитория (в нижнем регистре) → отсортированный массив уникальных
 * {@code actor.id}. Память ограничена лимитом пар на час (см. {@link Builder});
 * если лимит сработал, индекс помечается {@code truncated}.
 *
 * @param actorsByRepo  репозиторий → отсортированные уникальные акторы, поставившие звезду
 * @param hoursCovered  сколько часов окна попало в индекс
 * @param truncated     хотя бы один час обрезан лимитом памяти
 */
public record StarIndex(Map<String, long[]> actorsByRepo, int hoursCovered, boolean truncated) {

    private static final StarIndex EMPTY = new StarIndex(Map.of(), 0, false);

    public StarIndex {
        Objects.requireNonNull(actorsByRepo, "actorsByRepo");
        actorsByRepo = Map.copyOf(actorsByRepo);
        if (hoursCovered < 0) {
            throw new IllegalArgumentException("hoursCovered must be >= 0");
        }
    }

    /** Индекса нет: например, его не собирали или ни один час не загрузился. */
    public static StarIndex empty() {
        return EMPTY;
    }

    public boolean available() {
        return hoursCovered > 0;
    }

    /** Уникальные акторы, поставившие звезду репозиторию; пустой массив, если звёзд не было. */
    public long[] actors(String repoFullName) {
        long[] actors = actorsByRepo.get(normalize(repoFullName));
        return actors == null ? new long[0] : actors.clone();
    }

    /** Обход без копирования массивов. Массивы менять нельзя. */
    public void forEachRepo(BiConsumer<String, long[]> consumer) {
        actorsByRepo.forEach(consumer);
    }

    public int repoCount() {
        return actorsByRepo.size();
    }

    /** Число уникальных пар (репозиторий, актор). */
    public long pairCount() {
        long sum = 0;
        for (long[] actors : actorsByRepo.values()) {
            sum += actors.length;
        }
        return sum;
    }

    /** Объединяет почасовые индексы в индекс окна. */
    public static StarIndex merge(Collection<StarIndex> parts) {
        if (parts.isEmpty()) {
            return EMPTY;
        }
        if (parts.size() == 1) {
            return parts.iterator().next();
        }
        Map<String, long[]> merged = new HashMap<>();
        int hours = 0;
        boolean truncated = false;
        for (StarIndex part : parts) {
            hours += part.hoursCovered();
            truncated |= part.truncated();
            part.actorsByRepo().forEach((repo, actors) ->
                    merged.merge(repo, actors, StarIndex::unionSorted));
        }
        return new StarIndex(merged, hours, truncated);
    }

    public static String normalize(String repoFullName) {
        return repoFullName == null ? "" : repoFullName.toLowerCase(Locale.ROOT);
    }

    static long[] unionSorted(long[] left, long[] right) {
        long[] result = new long[left.length + right.length];
        int i = 0;
        int j = 0;
        int size = 0;
        while (i < left.length || j < right.length) {
            long next;
            if (j >= right.length || (i < left.length && left[i] <= right[j])) {
                next = left[i++];
            } else {
                next = right[j++];
            }
            if (size == 0 || result[size - 1] != next) {
                result[size++] = next;
            }
        }
        return size == result.length ? result : Arrays.copyOf(result, size);
    }

    /**
     * Накопитель индекса для одного часа (или фикстуры). Не потокобезопасен.
     * После {@code maxPairs} принятых пар новые пары отбрасываются, индекс помечается обрезанным.
     */
    public static final class Builder {

        private final long maxPairs;
        private final Map<String, LongBuffer> buffers = new HashMap<>();
        private long pairs;
        private boolean truncated;

        public Builder(long maxPairs) {
            if (maxPairs < 1) {
                throw new IllegalArgumentException("maxPairs must be >= 1");
            }
            this.maxPairs = maxPairs;
        }

        public void add(String repoFullName, long actorId) {
            if (pairs >= maxPairs) {
                truncated = true;
                return;
            }
            buffers.computeIfAbsent(normalize(repoFullName), key -> new LongBuffer()).add(actorId);
            pairs++;
        }

        /** Индекс одного часа (или фикстуры, которая считается одним покрытым часом). */
        public StarIndex build(int hoursCovered) {
            Map<String, long[]> result = new HashMap<>(Math.max(16, buffers.size() * 2));
            buffers.forEach((repo, buffer) -> result.put(repo, buffer.sortedDistinct()));
            return new StarIndex(result, hoursCovered, truncated);
        }
    }

    private static final class LongBuffer {

        private long[] values = new long[2];
        private int size;

        void add(long value) {
            if (size == values.length) {
                values = Arrays.copyOf(values, size * 2);
            }
            values[size++] = value;
        }

        long[] sortedDistinct() {
            long[] sorted = Arrays.copyOf(values, size);
            Arrays.sort(sorted);
            int distinct = 0;
            for (int i = 0; i < sorted.length; i++) {
                if (distinct == 0 || sorted[distinct - 1] != sorted[i]) {
                    sorted[distinct++] = sorted[i];
                }
            }
            return distinct == sorted.length ? sorted : Arrays.copyOf(sorted, distinct);
        }
    }
}
