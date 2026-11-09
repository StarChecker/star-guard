package ru.itmo.ipkn.starguard.core.scoring;

import java.util.Map;

/** Фиксированные значения мок-проверок для трёх демонстрационных профилей. */
final class MockProfiles {

    private static final double CONFIDENCE = 0.9;

    private static final Map<MockMode, Map<String, Double>> SCORES = Map.of(
            MockMode.HEALTHY, Map.of(
                    CheckIds.BURST, 0.10,
                    CheckIds.FRESH_ACCOUNTS, 0.10,
                    CheckIds.ENGAGEMENT, 0.15,
                    CheckIds.ACTOR_OVERLAP, 0.05,
                    CheckIds.CIRCADIAN, 0.10),
            MockMode.BORDERLINE, Map.of(
                    CheckIds.BURST, 0.40,
                    CheckIds.FRESH_ACCOUNTS, 0.35,
                    CheckIds.ENGAGEMENT, 0.45,
                    CheckIds.ACTOR_OVERLAP, 0.30,
                    CheckIds.CIRCADIAN, 0.20),
            MockMode.SUSPICIOUS, Map.of(
                    CheckIds.BURST, 0.95,
                    CheckIds.FRESH_ACCOUNTS, 0.90,
                    CheckIds.ENGAGEMENT, 0.85,
                    CheckIds.ACTOR_OVERLAP, 0.80,
                    CheckIds.CIRCADIAN, 0.60)
    );

    private MockProfiles() {
    }

    static double score(MockMode mode, String checkId) {
        return SCORES.getOrDefault(mode, Map.of()).getOrDefault(checkId, 0.0);
    }

    static double confidence() {
        return CONFIDENCE;
    }
}
