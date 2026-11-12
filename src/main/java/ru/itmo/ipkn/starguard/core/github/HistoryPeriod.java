package ru.itmo.ipkn.starguard.core.github;

import java.time.LocalDate;

/**
 * Запрошенный период истории GitHub: последние {@code days} дней, заканчивающиеся
 * датой {@code to} (включительно; {@code null} — сегодня), либо вся история
 * репозитория от даты создания.
 */
public record HistoryPeriod(Integer days, LocalDate to, boolean all) {

    public HistoryPeriod {
        if (all && days != null) {
            throw new IllegalArgumentException("days and all are mutually exclusive");
        }
    }

    public static HistoryPeriod days(Integer days) {
        return new HistoryPeriod(days, null, false);
    }

    public static HistoryPeriod until(Integer days, LocalDate to) {
        return new HistoryPeriod(days, to, false);
    }

    public static HistoryPeriod entireHistory() {
        return new HistoryPeriod(null, null, true);
    }
}
