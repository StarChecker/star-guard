package ru.itmo.ipkn.starguard.core.scoring.github;

import ru.itmo.ipkn.starguard.core.config.GithubChecksProperties;
import ru.itmo.ipkn.starguard.core.domain.GithubSignals;
import ru.itmo.ipkn.starguard.core.domain.RepoSnapshot;
import ru.itmo.ipkn.starguard.core.scoring.CheckIds;
import ru.itmo.ipkn.starguard.core.scoring.CheckResult;
import ru.itmo.ipkn.starguard.core.scoring.RepoCheck;

import java.util.Map;

public class ForkRatioCheck implements RepoCheck {

    private final GithubChecksProperties.ForkRatio properties;

    public ForkRatioCheck(GithubChecksProperties.ForkRatio properties) {
        this.properties = properties;
    }

    @Override
    public String id() {
        return CheckIds.FORK_RATIO;
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
        details.put("forks", activity.forks());
        details.put("forks_truncated", activity.forksTruncated());
        details.put("age_days", age);
        if (age > properties.maxAgeDays()) {
            details.put("reason", "not_young");
            return new CheckResult(id(), 0, 0, details);
        }
        if (activity.stars() * 4 < properties.minStars()) {
            details.put("reason", "too_few_stars");
            return new CheckResult(id(), 0, 0, details);
        }
        double ratio = (double) activity.forks() / Math.max(1, activity.stars());
        double score = Checks.clamp01((ratio - properties.floor()) / properties.span())
                * Math.min(1.0, (double) activity.stars() / properties.minStars());
        details.put("forks_per_star", Checks.round3(ratio));
        details.put("floor", properties.floor());
        return new CheckResult(id(), score, properties.confidence(), details);
    }
}
