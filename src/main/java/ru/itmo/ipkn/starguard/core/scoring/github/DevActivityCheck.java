package ru.itmo.ipkn.starguard.core.scoring.github;

import ru.itmo.ipkn.starguard.core.config.GithubChecksProperties;
import ru.itmo.ipkn.starguard.core.domain.GithubSignals;
import ru.itmo.ipkn.starguard.core.domain.RepoSnapshot;
import ru.itmo.ipkn.starguard.core.scoring.CheckIds;
import ru.itmo.ipkn.starguard.core.scoring.CheckResult;
import ru.itmo.ipkn.starguard.core.scoring.RepoCheck;

import java.time.ZoneOffset;
import java.util.Map;

public class DevActivityCheck implements RepoCheck {

    private static final int MIN_BASELINE_DAYS = 7;

    private final GithubChecksProperties.DevActivity properties;

    public DevActivityCheck(GithubChecksProperties.DevActivity properties) {
        this.properties = properties;
    }

    @Override
    public String id() {
        return CheckIds.DEV_ACTIVITY;
    }

    @Override
    public CheckResult evaluate(RepoSnapshot snapshot) {
        GithubSignals github = snapshot.github();
        if (github == null) {
            return Checks.inactive(id(), "no_github_data");
        }
        GithubSignals.WindowActivity activity = github.activity();
        long age = github.ageDays(snapshot.capturedAt());
        Map<String, Object> details = Checks.details();
        details.put("stars", activity.stars());
        details.put("issues", activity.issues());
        details.put("pull_requests", activity.pullRequests());
        details.put("commits", activity.commits());
        details.put("contributors", activity.contributors());
        details.put("days", activity.days());
        details.put("age_days", age);
        if (activity.stars() * 4 < properties.minStars()) {
            details.put("reason", "too_few_stars");
            return new CheckResult(id(), 0, 0, details);
        }

        double unexplained = activity.stars();
        if (age > properties.maxAgeDays()) {
            int[] daily = github.dailyStars();
            int start = (int) Math.max(0, activity.from().atZone(ZoneOffset.UTC).toLocalDate().toEpochDay()
                    - github.firstDay().toEpochDay());
            int baseFrom = Math.max(0, start - properties.baselineDays());
            if (start - baseFrom >= MIN_BASELINE_DAYS) {
                double baselinePerDay = Checks.median(daily, baseFrom, start);
                double expected = baselinePerDay * (daily.length - start);
                unexplained = Math.max(0, activity.stars() - expected);
                details.put("baseline_per_day", Checks.round3(baselinePerDay));
                if (unexplained < properties.unexplainedShare() * activity.stars()
                        || unexplained < properties.minStars()) {
                    details.put("steady_inflow", true);
                    details.put("unexplained_stars", (int) Math.round(unexplained));
                    return new CheckResult(id(), 0, properties.confidence(), details);
                }
            }
        }
        double ratio = activity.development() / Math.max(1.0, unexplained);
        double score = Checks.clamp01((properties.floor() - ratio) / properties.floor())
                * Math.min(1.0, unexplained / properties.minStars());
        details.put("unexplained_stars", (int) Math.round(unexplained));
        details.put("activity_per_star", Checks.round3(ratio));
        details.put("floor", properties.floor());
        return new CheckResult(id(), score, properties.confidence(), details);
    }
}
