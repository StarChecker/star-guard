package ru.itmo.ipkn.starguard.core.scoring.github;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import ru.itmo.ipkn.starguard.core.config.BurstProperties;
import ru.itmo.ipkn.starguard.core.config.GithubChecksProperties;
import ru.itmo.ipkn.starguard.core.config.ScoringProperties;
import ru.itmo.ipkn.starguard.core.domain.RepoSnapshot;
import ru.itmo.ipkn.starguard.core.scoring.DefaultScoringEngine;
import ru.itmo.ipkn.starguard.core.scoring.RepoCheck;
import ru.itmo.ipkn.starguard.core.scoring.RepoScore;

import java.time.Clock;
import java.util.List;

@Component
public class GithubScoringEngine {

    public static final String PREFIX = "app.scoring.github";

    private final DefaultScoringEngine engine;

    @Autowired
    public GithubScoringEngine(
            GithubChecksProperties checks, BurstProperties burstProperties, Environment environment, Clock clock) {
        this(checks, burstProperties, Binder.get(environment)
                .bind(PREFIX, Bindable.of(ScoringProperties.class))
                .orElseGet(() -> new ScoringProperties(null, null, null, null, null)), clock);
    }

    public GithubScoringEngine(
            GithubChecksProperties checks, BurstProperties burstProperties, ScoringProperties scoring, Clock clock) {
        List<RepoCheck> all = List.of(
                new DailyBurstCheck(checks.dailyBurst()),
                new DevActivityCheck(checks.devActivity()),
                new ForkRatioCheck(checks.forkRatio()),
                new IntradayBurstCheck(checks.intradayBurst(), burstProperties),
                new HourlyProfileCheck(checks.hourlyProfile()),
                new StarDropsCheck(checks.starDrops()));
        this.engine = new DefaultScoringEngine(all, scoring, clock);
    }

    public RepoScore score(RepoSnapshot snapshot) {
        return engine.score(snapshot);
    }
}
