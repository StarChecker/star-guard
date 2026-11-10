package ru.itmo.ipkn.starguard.core.scoring;

/** Уровень подозрительности по итоговому score 0..100. */
public enum ScoreLevel {
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL;

    public static ScoreLevel fromScore(int score, int mediumFrom, int highFrom, int criticalFrom) {
        if (score >= criticalFrom) {
            return CRITICAL;
        }
        if (score >= highFrom) {
            return HIGH;
        }
        if (score >= mediumFrom) {
            return MEDIUM;
        }
        return LOW;
    }
}
