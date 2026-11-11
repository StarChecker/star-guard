package ru.itmo.ipkn.starguard.core.domain;

import java.time.Instant;

/**
 * Откуда взяты данные отчёта.
 *
 * @param type          источник
 * @param starsSampled  сколько звёзд загружено (для истории GitHub)
 * @param starsTotal    сколько звёзд у репозитория всего (для истории GitHub)
 * @param coverageFrom  с какого момента история полная (для истории GitHub)
 */
public record DataSource(Type type, Integer starsSampled, Integer starsTotal, Instant coverageFrom) {

    public enum Type {
        /** индекс топ-N по GH Archive */
        GH_ARCHIVE_INDEX,
        /** прямой скан часовых дампов GH Archive */
        GH_ARCHIVE,
        /** история репозитория из GitHub API */
        GITHUB_API
    }

    public static DataSource archive() {
        return new DataSource(Type.GH_ARCHIVE, null, null, null);
    }

    public static DataSource index() {
        return new DataSource(Type.GH_ARCHIVE_INDEX, null, null, null);
    }
}
