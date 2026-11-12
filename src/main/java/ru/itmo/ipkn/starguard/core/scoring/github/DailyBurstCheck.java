package ru.itmo.ipkn.starguard.core.scoring.github;

import ru.itmo.ipkn.starguard.core.config.GithubChecksProperties;
import ru.itmo.ipkn.starguard.core.domain.GithubSignals;
import ru.itmo.ipkn.starguard.core.domain.RepoSnapshot;
import ru.itmo.ipkn.starguard.core.scoring.CheckIds;
import ru.itmo.ipkn.starguard.core.scoring.CheckResult;
import ru.itmo.ipkn.starguard.core.scoring.RepoCheck;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class DailyBurstCheck implements RepoCheck {

    private final GithubChecksProperties.DailyBurst properties;

    public DailyBurstCheck(GithubChecksProperties.DailyBurst properties) {
        this.properties = properties;
    }

    @Override
    public String id() {
        return CheckIds.DAILY_BURST;
    }

    @Override
    public CheckResult evaluate(RepoSnapshot snapshot) {
        GithubSignals github = snapshot.github();
        if (github == null) {
            return Checks.inactive(id(), "no_github_data");
        }
        int[] daily = github.dailyStars();
        LocalDate windowStart = github.activity().from().atZone(ZoneOffset.UTC).toLocalDate();
        int start = (int) Math.max(0, windowStart.toEpochDay() - github.firstDay().toEpochDay());

        int evaluated = 0;
        int windowDays = 0;
        double bestScore = 0;
        int bestIndex = -1;
        double bestBaseline = 0;
        double bestRatio = 0;
        List<Map<String, Object>> bursts = new ArrayList<>();
        for (int i = start; i < daily.length; i++) {
            windowDays++;
            int from = Math.max(0, i - properties.baselineDays());
            if (i - from < properties.minBaselineDays()) {
                continue;
            }
            evaluated++;
            double baseline = Checks.median(daily, from, i);
            double excess = daily[i] - baseline;
            double ratio = daily[i] / Math.max(baseline, 1.0);
            double score = Checks.clamp01((ratio - properties.ratioFloor()) / properties.ratioSpan())
                    * Math.min(1.0, Math.max(0.0, excess) / properties.minExcess());
            if (score > bestScore || bestIndex < 0) {
                bestScore = score;
                bestIndex = i;
                bestBaseline = baseline;
                bestRatio = ratio;
            }
            if (ratio >= properties.ratioFloor() && excess >= properties.minExcess()) {
                Map<String, Object> burst = new LinkedHashMap<>();
                burst.put("day", github.day(i).toString());
                burst.put("stars", daily[i]);
                burst.put("baseline", Checks.round3(baseline));
                burst.put("excess", (int) Math.round(excess));
                bursts.add(burst);
            }
        }
        if (evaluated == 0) {
            return Checks.inactive(id(), "no_baseline");
        }
        bursts.sort(Comparator.comparingInt((Map<String, Object> burst) -> (int) burst.get("excess")).reversed());

        Map<String, Object> details = Checks.details();
        details.put("best_day", github.day(bestIndex).toString());
        details.put("best_stars", daily[bestIndex]);
        details.put("baseline", Checks.round3(bestBaseline));
        details.put("ratio", Checks.round3(bestRatio));
        details.put("burst_days", bursts.stream().limit(properties.topDays()).toList());
        details.put("burst_day_count", bursts.size());
        details.put("baseline_days", properties.baselineDays());
        details.put("evaluated_days", evaluated);
        details.put("ratio_floor", properties.ratioFloor());
        details.put("min_excess", properties.minExcess());
        double coverage = (double) evaluated / Math.max(1, windowDays);
        if (coverage < 1.0) {
            details.put("reason", "partial_baseline");
        }
        return new CheckResult(id(), bestScore, properties.confidence() * coverage, details);
    }
}
