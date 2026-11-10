package ru.itmo.ipkn.starguard.core.scoring;

/**
 * Какая реализация проверки подключена: мок из демо-профиля или реальный алгоритм.
 * <p>
 * Задаётся общим флагом {@code app.scoring.mode} и точечно —
 * {@code app.scoring.checks.<id>.mode}. Значение валидируется при старте:
 * опечатка роняет приложение, а не отключает проверку молча.
 */
public enum CheckMode {
    MOCK,
    REAL
}
