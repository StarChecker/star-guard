package ru.itmo.ipkn.starguard.core.index;

import ru.itmo.ipkn.starguard.core.archive.HourReadResult;
import ru.itmo.ipkn.starguard.core.domain.StarIndex;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * Итог одного прохода по часу GH Archive: компактные агрегаты всех репозиториев
 * часа и индекс звёзд для C4.
 * <p>
 * Дайджест неизменяем и переживает пересборку индекса: при сдвиге окна часы,
 * которые в нём остались, не скачиваются и не разбираются повторно, а репозиторий,
 * впервые попавший в топ, получает данные за прошлые часы из уже готовых дайджестов.
 *
 * @param repos        агрегаты по нормализованному имени репозитория (все репозитории часа)
 * @param displayNames имя репозитория в исходном регистре по нормализованному имени
 */
public record HourDigest(
        Instant hour,
        HourReadResult readResult,
        Map<String, RepoHourAggregate> repos,
        Map<String, String> displayNames,
        StarIndex starIndex
) {

    public HourDigest {
        Objects.requireNonNull(hour, "hour");
        Objects.requireNonNull(readResult, "readResult");
        repos = Map.copyOf(Objects.requireNonNull(repos, "repos"));
        displayNames = Map.copyOf(Objects.requireNonNull(displayNames, "displayNames"));
        starIndex = starIndex == null ? StarIndex.empty() : starIndex;
    }

    /** Час без данных: дамп не опубликован (404) или не скачался после ретраев. */
    public static HourDigest unavailable(HourReadResult readResult) {
        return new HourDigest(readResult.hour(), readResult, Map.of(), Map.of(), StarIndex.empty());
    }

    /** Час загружен; только такие дайджесты переиспользуются при следующей сборке. */
    public boolean loaded() {
        return !readResult.missing() && !readResult.failed();
    }

    /** Агрегат репозитория за этот час или пустой, если событий не было. */
    public RepoHourAggregate aggregateOrEmpty(String normalizedName) {
        RepoHourAggregate aggregate = repos.get(normalizedName);
        return aggregate == null ? RepoHourAggregate.empty(hour) : aggregate;
    }

    /** Число записей об акторах во всех агрегатах часа — грубая мера занимаемой памяти. */
    public int actorRecords() {
        return repos.values().stream().mapToInt(aggregate -> aggregate.actors().size()).sum();
    }
}
