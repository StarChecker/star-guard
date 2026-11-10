package ru.itmo.ipkn.starguard.core.domain;

/**
 * Какие блоки данных, нужные проверкам C1–C5, реально доступны.
 * GH Archive не содержит истории аккаунтов, а глобальный часовой профиль появится
 * только с Redis-агрегацией. {@code crossRepoOverlap} включается, когда клиент
 * архива собрал {@link StarIndex} — индекс звёзд по всем репозиториям окна.
 */
public record DataAvailability(
        boolean actorHistory,
        boolean crossRepoOverlap,
        boolean globalHourProfile
) {

    public static DataAvailability pocDefaults() {
        return new DataAvailability(false, false, false);
    }

    public DataAvailability withCrossRepoOverlap(boolean available) {
        return new DataAvailability(actorHistory, available, globalHourProfile);
    }
}
