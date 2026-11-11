package ru.itmo.ipkn.starguard.core.scoring;

import ru.itmo.ipkn.starguard.core.config.BurstProperties;
import ru.itmo.ipkn.starguard.core.domain.RepoSnapshot;

import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/** C1: доля звёзд, пришедших в самое насыщенное десятиминутное окно. */
public class BurstCheck implements RepoCheck {

    private static final int WINDOW_MINUTES = 10;
    private final BurstProperties properties;

    public BurstCheck(BurstProperties properties) {
        this.properties = properties;
    }

    @Override
    public String id() {
        return CheckIds.BURST;
    }

    @Override
    public CheckResult evaluate(RepoSnapshot snapshot) {
        int[] minutes = snapshot.starsByMinute();
        if (minutes == null || minutes.length == 0) {
            return new CheckResult(id(), 0, 0,
                    Map.of("mocked", false, "reason", "missing_stars_by_minute"));
        }

        int windowSize = Math.min(WINDOW_MINUTES, minutes.length);
        long sum = 0;
        for (int i = 0; i < windowSize; i++) {
            sum += minutes[i];
        }
        long maxBurst = sum;
        int bestStart = 0;
        for (int end = windowSize; end < minutes.length; end++) {
            sum += (long) minutes[end] - minutes[end - windowSize];
            if (sum > maxBurst) {
                maxBurst = sum;
                bestStart = end - windowSize + 1;
            }
        }

        int[] sorted = minutes.clone();
        Arrays.sort(sorted);
        int middle = sorted.length / 2;
        double median = sorted.length % 2 == 0
                ? ((double) sorted[middle - 1] + sorted[middle]) / 2.0
                : sorted[middle];
        double baseline10m = median * WINDOW_MINUTES;
        double ratio = maxBurst / Math.max(baseline10m, 1.0);
        double s1 = Math.clamp((ratio - properties.ratioFloor()) / properties.ratioSpan(), 0.0, 1.0);
        double significance = Math.min(1.0, (double) maxBurst / properties.minBurst());
        double confidence = minutes.length >= properties.warmupMinutes()
                ? 1.0
                : (double) minutes.length / (2.0 * properties.warmupMinutes());

        Map<String, Object> details = new HashMap<>();
        details.put("mocked", false);
        details.put("window_start", snapshot.window().from().plus(bestStart, ChronoUnit.MINUTES).toString());
        details.put("max_burst_10m", maxBurst);
        details.put("ratio", ratio);
        details.put("baseline_10m", baseline10m);
        details.put("observed_minutes", minutes.length);
        if (minutes.length < properties.warmupMinutes()) {
            details.put("reason", "insufficient_history");
        }
        return new CheckResult(id(), s1 * significance, confidence, details);
    }
}
