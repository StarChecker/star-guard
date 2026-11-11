package ru.itmo.ipkn.starguard.core.domain;

import java.time.Instant;

/**
 * Откуда взяты данные отчёта.
 *
 * @param type          источник
 * @param starsSampled  сколько звёзд загружено (для истории GitHub)
 * @param starsTotal    сколько звёзд у репозитория всего (для истории GitHub)
 * @param coverageFrom  с какого момента история полная (для истории GitHub)
 * @param tier          уровень индекса для {@link Type#GH_ARCHIVE_INDEX}; null для остальных источников
 */
public record DataSource(Type type, Integer starsSampled, Integer starsTotal, Instant coverageFrom, Tier tier) {

    public enum Type {
        /** индекс GH Archive: топ-N или long-tail окна */
        GH_ARCHIVE_INDEX,
        /** прямой скан часовых дампов GH Archive */
        GH_ARCHIVE,
        /** история репозитория из GitHub API */
        GITHUB_API
    }

    /** Уровень индекса, из которого отдан отчёт. */
    public enum Tier {
        /** топ-N по звёздам за окно */
        TOP,
        /** остальные репозитории окна: агрегаты собираются из почасовых дайджестов */
        LONG_TAIL
    }

    public DataSource(Type type, Integer starsSampled, Integer starsTotal, Instant coverageFrom) {
        this(type, starsSampled, starsTotal, coverageFrom, null);
    }

    public static DataSource archive() {
        return new DataSource(Type.GH_ARCHIVE, null, null, null);
    }

    public static DataSource index() {
        return index(Tier.TOP);
    }

    public static DataSource index(Tier tier) {
        return new DataSource(Type.GH_ARCHIVE_INDEX, null, null, null, tier);
    }
}
