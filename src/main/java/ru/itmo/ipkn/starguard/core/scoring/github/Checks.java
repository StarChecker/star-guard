package ru.itmo.ipkn.starguard.core.scoring.github;

import ru.itmo.ipkn.starguard.core.scoring.CheckResult;

import java.util.HashMap;
import java.util.Map;

final class Checks {

    private Checks() {
    }

    static CheckResult inactive(String id, String reason) {
        return new CheckResult(id, 0, 0, Map.of("mocked", false, "reason", reason));
    }

    static Map<String, Object> details() {
        Map<String, Object> details = new HashMap<>();
        details.put("mocked", false);
        return details;
    }

    static double clamp01(double value) {
        return Double.isFinite(value) ? Math.clamp(value, 0.0, 1.0) : 0.0;
    }

    static double round3(double value) {
        return Math.round(value * 1000.0) / 1000.0;
    }

    static double median(int[] values, int from, int to) {
        int[] slice = java.util.Arrays.copyOfRange(values, from, to);
        java.util.Arrays.sort(slice);
        int middle = slice.length / 2;
        return slice.length % 2 == 0 ? (slice[middle - 1] + slice[middle]) / 2.0 : slice[middle];
    }
}
