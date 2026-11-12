package ru.itmo.ipkn.starguard.core.tracker;

import ru.itmo.ipkn.starguard.core.domain.GithubSignals;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class IntradayAssembler {

    public static final int STEP_MINUTES = 5;

    private IntradayAssembler() {
    }

    public static Instant rawFrom(Instant now) {
        Duration step = Duration.ofMinutes(STEP_MINUTES);
        long seconds = step.toSeconds();
        Instant from = now.minus(Duration.ofDays(1)).minus(step);
        return Instant.ofEpochSecond(Math.floorDiv(from.getEpochSecond(), seconds) * seconds);
    }

    public static Instant hourlyFrom(Instant now, int profileDays) {
        return now.truncatedTo(ChronoUnit.HOURS).minus(Duration.ofDays(profileDays));
    }

    public static GithubSignals.Intraday assemble(
            Instant now,
            List<TrackerStore.Point> raw,
            List<TrackerStore.Point> hourlyPoints,
            int profileDays,
            double[] globalHourShare,
            Instant trackedSince) {
        Instant rawFrom = rawFrom(now);
        int slots = (int) (Duration.between(rawFrom, now).toMinutes() / STEP_MINUTES) + 1;
        int[] stars = missing(slots);
        for (TrackerStore.Point point : raw) {
            int slot = (int) Math.floorDiv(Duration.between(rawFrom, point.at()).toMinutes(), STEP_MINUTES);
            if (slot >= 0 && slot < slots) {
                stars[slot] = point.stars();
            }
        }

        Instant hourlyFrom = hourlyFrom(now, profileDays);
        int hours = (int) Duration.between(hourlyFrom, now.truncatedTo(ChronoUnit.HOURS)).toHours() + 1;
        int[] hourly = missing(hours);
        for (TrackerStore.Point point : hourlyPoints) {
            int index = (int) Duration.between(hourlyFrom, point.at()).toHours();
            if (index >= 0 && index < hours) {
                hourly[index] = point.stars();
            }
        }
        Map<Instant, Integer> lastRawByHour = new HashMap<>();
        for (TrackerStore.Point point : raw) {
            lastRawByHour.merge(point.at().truncatedTo(ChronoUnit.HOURS), point.stars(), (old, value) -> value);
        }
        lastRawByHour.forEach((at, value) -> {
            int index = (int) Duration.between(hourlyFrom, at).toHours();
            if (index >= 0 && index < hours && hourly[index] == GithubSignals.Intraday.MISSING) {
                hourly[index] = value;
            }
        });
        Instant since = trackedSince != null ? trackedSince : raw.isEmpty() ? now : raw.getFirst().at();
        return new GithubSignals.Intraday(rawFrom, STEP_MINUTES, stars, hourlyFrom, hourly, globalHourShare, since);
    }

    private static int[] missing(int size) {
        int[] values = new int[Math.max(0, size)];
        Arrays.fill(values, GithubSignals.Intraday.MISSING);
        return values;
    }
}
