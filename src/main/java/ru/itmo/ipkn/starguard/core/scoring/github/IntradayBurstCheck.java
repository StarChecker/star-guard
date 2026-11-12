package ru.itmo.ipkn.starguard.core.scoring.github;

import ru.itmo.ipkn.starguard.core.config.BurstProperties;
import ru.itmo.ipkn.starguard.core.config.GithubChecksProperties;
import ru.itmo.ipkn.starguard.core.domain.DataAvailability;
import ru.itmo.ipkn.starguard.core.domain.GithubSignals;
import ru.itmo.ipkn.starguard.core.domain.RepoSnapshot;
import ru.itmo.ipkn.starguard.core.domain.TimeWindow;
import ru.itmo.ipkn.starguard.core.scoring.BurstCheck;
import ru.itmo.ipkn.starguard.core.scoring.CheckIds;
import ru.itmo.ipkn.starguard.core.scoring.CheckResult;
import ru.itmo.ipkn.starguard.core.scoring.RepoCheck;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

public class IntradayBurstCheck implements RepoCheck {

    private static final int DAY_MINUTES = 24 * 60;

    private final GithubChecksProperties.IntradayBurst properties;
    private final BurstCheck burst;

    public IntradayBurstCheck(GithubChecksProperties.IntradayBurst properties, BurstProperties burstProperties) {
        this.properties = properties;
        this.burst = new BurstCheck(burstProperties);
    }

    @Override
    public String id() {
        return CheckIds.INTRADAY_BURST;
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
        int step = intraday.stepMinutes();
        int[] stars = intraday.stars();
        int slots = Math.min(stars.length, DAY_MINUTES / step + 1);
        int first = stars.length - slots;
        Instant start = intraday.from().plus(Duration.ofMinutes((long) first * step));
        int[] minutes = new int[DAY_MINUTES];
        int known = 0;
        for (int slot = first + 1; slot < stars.length; slot++) {
            if (stars[slot] < 0 || stars[slot - 1] < 0) {
                continue;
            }
            known++;
            int delta = Math.max(0, stars[slot] - stars[slot - 1]);
            int base = (slot - first - 1) * step;
            for (int m = 0; m < step && base + m < DAY_MINUTES; m++) {
                minutes[base + m] += delta / step + (m < delta % step ? 1 : 0);
            }
        }
        double coverage = (double) known / Math.max(1, slots - 1);
        if (coverage < properties.minCoverage()) {
            Map<String, Object> details = Checks.details();
            details.put("reason", "insufficient_tracking");
            details.put("coverage", Checks.round3(coverage));
            return new CheckResult(id(), 0, 0, details);
        }
        RepoSnapshot minuteSnapshot = new RepoSnapshot(snapshot.repo(),
                new TimeWindow(start, start.plus(Duration.ofDays(1)), 24), snapshot.capturedAt(),
                snapshot.counts(), 0, 0, minutes, DataAvailability.pocDefaults());
        CheckResult result = burst.evaluate(minuteSnapshot);
        Map<String, Object> details = new HashMap<>(result.details());
        details.remove("reason");
        details.put("coverage", Checks.round3(coverage));
        details.put("step_minutes", step);
        return new CheckResult(id(), result.score(), properties.confidence() * coverage, details);
    }
}
