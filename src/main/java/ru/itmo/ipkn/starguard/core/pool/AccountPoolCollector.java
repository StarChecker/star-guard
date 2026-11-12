package ru.itmo.ipkn.starguard.core.pool;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.TaskScheduler;
import ru.itmo.ipkn.starguard.core.config.AccountPoolProperties;
import ru.itmo.ipkn.starguard.core.config.GithubProperties;
import ru.itmo.ipkn.starguard.core.github.GithubAccountPoolClient;
import java.time.Clock;
import java.util.List;

public class AccountPoolCollector {
    private static final Logger log = LoggerFactory.getLogger(AccountPoolCollector.class);
    private final AccountPoolStore store;
    private final GithubAccountPoolClient client;
    private final AccountPoolProperties p;
    private final GithubProperties github;
    private final TaskScheduler scheduler;
    private final Clock clock;
    public AccountPoolCollector(AccountPoolStore store, GithubAccountPoolClient client, AccountPoolProperties p,
                                GithubProperties github, TaskScheduler scheduler, Clock clock) {
        this.store = store; this.client = client; this.p = p; this.github = github;
        this.scheduler = scheduler; this.clock = clock;
    }
    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        if (github.token().isBlank()) { log.warn("Account pool enabled without GitHub token; collection disabled"); return; }
        scheduler.scheduleWithFixedDelay(this::runSafely, clock.instant(), p.interval());
    }
    private void runSafely() {
        try { collect(); }
        catch (RuntimeException e) { log.warn("Account pool refresh interrupted; existing evidence retained", e); }
    }
    public synchronized void collect() {
        var now = clock.instant();
        // Retry old accounts before pruning, so a short outage does not discard the whole pool.
        for (String id : store.due(now.minus(p.refreshAfter()), now, p.refreshBatch())) {
            if (!client.hasBudget(p.rateReserve(), clock.instant())) return;
            try {
                refresh(id);
            } catch (RuntimeException e) {
                store.defer(id, now.plus(p.refreshAfter()));
                throw e;
            }
        }
        store.purge(now.minus(p.staleAfter()), p.maxAccounts());
        if (!client.hasBudget(p.rateReserve(), clock.instant())) return;
        var seed = store.nextSeed(now);
        if (seed.isEmpty()) return;
        try {
            var page = client.forks(seed.get().id(), seed.get().cursor(), p.discoveryBatch());
            for (String id : page.ids()) {
                if (store.recentlyChecked(id, now.minus(p.refreshAfter()))) continue;
                if (!client.hasBudget(p.rateReserve(), clock.instant())) return;
                refresh(id);
            }
            // Advance only after processing every candidate; cooldown skips users on budget retries.
            store.advance(seed.get(), page.nextCursor(), now);
        } catch (RuntimeException e) {
            // A bad cursor or inaccessible repository must not starve all other seeds.
            store.advance(seed.get(), null, now);
            throw e;
        }
    }
    private void refresh(String id) {
        var profile = client.profile(id);
        double score = profile.map(value -> AccountClassifier.score(value, p)).orElse(0.0);
        store.save(id, score, profile.map(GithubAccountPoolClient.Profile::stars).orElse(List.of()),
                clock.instant(), p.maxAccounts());
    }
}
