package ru.itmo.ipkn.starguard.core.scoring.github;

import ru.itmo.ipkn.starguard.core.config.GithubChecksProperties;
import ru.itmo.ipkn.starguard.core.domain.GithubSignals;
import ru.itmo.ipkn.starguard.core.domain.RepoSnapshot;
import ru.itmo.ipkn.starguard.core.scoring.CheckIds;
import ru.itmo.ipkn.starguard.core.scoring.CheckResult;
import ru.itmo.ipkn.starguard.core.scoring.RepoCheck;

import java.time.Duration;
import java.util.Map;

public class StarDropsCheck implements RepoCheck {

    private static final int TRACKED_HOURS = 7 * 24;

    private final GithubChecksProperties.StarDrops properties;

    public StarDropsCheck(GithubChecksProperties.StarDrops properties) {
        this.properties = properties;
    }

    @Override
    public String id() {
        return CheckIds.STAR_DROPS;
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
        int[] hourly = intraday.hourlyStars();
        long dropped = 0;
        long gained = 0;
        int known = 0;
        int biggest = 0;
        int biggestIndex = -1;
        for (int i = 1; i < hourly.length; i++) {
            if (hourly[i] < 0 || hourly[i - 1] < 0) {
                continue;
            }
            known++;
            int delta = hourly[i] - hourly[i - 1];
            if (delta < 0) {
                dropped -= delta;
                if (-delta > biggest) {
                    biggest = -delta;
                    biggestIndex = i;
                }
            } else {
                gained += delta;
            }
        }
        Map<String, Object> details = Checks.details();
        details.put("tracked_hours", known);
        details.put("dropped", dropped);
        details.put("gained", gained);
        if (known < properties.minHours()) {
            details.put("reason", "insufficient_tracking");
            return new CheckResult(id(), 0, 0, details);
        }
        double share = (double) dropped / Math.max(1, dropped + gained);
        double score = Checks.clamp01((share - properties.shareFloor()) / properties.shareSpan())
                * Math.min(1.0, (double) dropped / properties.minDropped());
        details.put("drop_share", Checks.round3(share));
        details.put("share_floor", properties.shareFloor());
        if (biggestIndex > 0) {
            details.put("biggest_drop", biggest);
            details.put("biggest_drop_at",
                    intraday.hourlyFrom().plus(Duration.ofHours(biggestIndex)).toString());
        }
        double coverage = Math.min(1.0, (double) known / TRACKED_HOURS);
        return new CheckResult(id(), score, properties.confidence() * Math.max(coverage, 0.5), details);
    }
}
