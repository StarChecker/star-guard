package ru.itmo.ipkn.starguard.core.scoring.github;

import ru.itmo.ipkn.starguard.core.config.GithubChecksProperties;
import ru.itmo.ipkn.starguard.core.domain.GithubSignals;
import ru.itmo.ipkn.starguard.core.domain.RepoSnapshot;
import ru.itmo.ipkn.starguard.core.scoring.CheckIds;
import ru.itmo.ipkn.starguard.core.scoring.CheckResult;
import ru.itmo.ipkn.starguard.core.scoring.RepoCheck;

import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

public class HourlyProfileCheck implements RepoCheck {

    private static final double MIN_SHARE = 1e-4;
    private static final int TRACKED_DAYS = 7;

    private final GithubChecksProperties.HourlyProfile properties;

    public HourlyProfileCheck(GithubChecksProperties.HourlyProfile properties) {
        this.properties = properties;
    }

    @Override
    public String id() {
        return CheckIds.HOURLY_PROFILE;
    }

    @Override
    public CheckResult evaluate(RepoSnapshot snapshot) {
        GithubSignals github = snapshot.github();
        if (github == null) {
            return Checks.inactive(id(), "no_github_data");
        }
        GithubSignals.Intraday intraday = github.intraday();
        if (intraday == null) {
            return Checks.inactive(id(), "not_tracked");
        }
        double[] global = intraday.globalHourShare();
        if (global.length != 24) {
            return Checks.inactive(id(), "no_global_profile");
        }
        int[] hourly = intraday.hourlyStars();
        int startHour = intraday.hourlyFrom().atZone(ZoneOffset.UTC).getHour();
        long[] bins = new long[24];
        int known = 0;
        long stars = 0;
        for (int i = 1; i < hourly.length; i++) {
            if (hourly[i] < 0 || hourly[i - 1] < 0) {
                continue;
            }
            known++;
            int delta = Math.max(0, hourly[i] - hourly[i - 1]);
            bins[(startHour + i) % 24] += delta;
            stars += delta;
        }
        Map<String, Object> details = Checks.details();
        details.put("tracked_hours", known);
        details.put("stars", stars);
        if (known < properties.minDays() * 24) {
            details.put("reason", "insufficient_tracking");
            return new CheckResult(id(), 0, 0, details);
        }
        double divergence = 0;
        for (int hour = 0; hour < 24; hour++) {
            if (bins[hour] == 0 || stars == 0) {
                continue;
            }
            double p = (double) bins[hour] / stars;
            divergence += p * (Math.log(p / Math.max(global[hour], MIN_SHARE)) / Math.log(2));
        }
        double score = Checks.clamp01((divergence - properties.klFloor()) / properties.klSpan())
                * Math.min(1.0, (double) stars / properties.minStars());
        List<Integer> peaks = new ArrayList<>(IntStream.range(0, 24).boxed().toList());
        peaks.sort(Comparator.comparingLong((Integer hour) -> bins[hour]).reversed());
        details.put("kl_divergence", Checks.round3(divergence));
        details.put("peak_hours_utc", peaks.subList(0, 2));
        details.put("kl_floor", properties.klFloor());
        double coverage = Math.min(1.0, known / (TRACKED_DAYS * 24.0));
        return new CheckResult(id(), score, properties.confidence() * coverage, details);
    }
}
