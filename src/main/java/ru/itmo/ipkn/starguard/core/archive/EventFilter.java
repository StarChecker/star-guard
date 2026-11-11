package ru.itmo.ipkn.starguard.core.archive;

import ru.itmo.ipkn.starguard.core.domain.EventType;
import ru.itmo.ipkn.starguard.core.domain.GhEvent;

import java.util.Set;

/**
 * Ранний фильтр строк дампа GH Archive.
 * <p>
 * Парсер спрашивает фильтр сразу после поля {@code type} и после объекта {@code repo};
 * если фильтр ответил {@code false}, остаток строки (обычно это тяжёлый {@code payload})
 * не разбирается. Поля, которые ещё не прочитаны, передаются как {@code null}:
 * пока данных не хватает для решения, фильтр обязан вернуть {@code true}.
 * Готовое событие проверяется ещё раз с полными данными, поэтому порядок полей
 * в строке на результат не влияет — только на то, насколько рано удастся остановиться.
 */
@FunctionalInterface
public interface EventFilter {

    /** Пропускает всё: полный разбор каждой строки. */
    EventFilter ALL = (type, repoFullName) -> true;

    /**
     * @param type         тип события или {@code null}, если поле ещё не прочитано
     * @param repoFullName {@code repo.name} или {@code null}, если поле ещё не прочитано
     * @return {@code false}, только если событие точно не нужно
     */
    boolean mayAccept(EventType type, String repoFullName);

    /** Окончательная проверка разобранного события. */
    default boolean accepts(GhEvent event) {
        return mayAccept(event.type(), event.repoFullName());
    }

    /** Событие нужно, если его пропускает этот фильтр или {@code other}. */
    default EventFilter or(EventFilter other) {
        return (type, repoFullName) ->
                mayAccept(type, repoFullName) || other.mayAccept(type, repoFullName);
    }

    /** Только звёзды ({@code WatchEvent}) любых репозиториев — лёгкий проход 1 индекса. */
    static EventFilter stars() {
        return (type, repoFullName) -> type == null || type == EventType.WATCH;
    }

    /** События одного репозитория, имя сравнивается без учёта регистра. */
    static EventFilter repo(String fullName) {
        return (type, repoFullName) -> repoFullName == null || repoFullName.equalsIgnoreCase(fullName);
    }

    /**
     * События репозиториев из набора.
     *
     * @param normalizedNames имена, приведённые {@link GhArchiveParser#normalizeFullName(String)}
     */
    static EventFilter repos(Set<String> normalizedNames) {
        return (type, repoFullName) -> repoFullName == null
                || normalizedNames.contains(GhArchiveParser.normalizeFullName(repoFullName));
    }
}
