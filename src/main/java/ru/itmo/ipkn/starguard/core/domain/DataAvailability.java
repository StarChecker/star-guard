package ru.itmo.ipkn.starguard.core.domain;

/**
 * Какие блоки данных, нужные проверкам C1–C5, реально доступны.
 * В PoC все три блока недоступны: GH Archive не содержит истории аккаунтов,
 * а глобальные индексы появятся только с Redis-агрегацией.
 */
public record DataAvailability(
        boolean actorHistory,
        boolean crossRepoOverlap,
        boolean globalHourProfile
) {

    public static DataAvailability pocDefaults() {
        return new DataAvailability(false, false, false);
    }
}
