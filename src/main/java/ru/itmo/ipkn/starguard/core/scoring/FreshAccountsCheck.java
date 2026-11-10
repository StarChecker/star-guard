package ru.itmo.ipkn.starguard.core.scoring;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import ru.itmo.ipkn.starguard.core.config.FreshAccountsProperties;
import ru.itmo.ipkn.starguard.core.domain.ActorProfile;
import ru.itmo.ipkn.starguard.core.domain.RepoSnapshot;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
@ConditionalOnProperty(name = FreshAccountsProperties.MODE_PROPERTY, havingValue = "real")
public class FreshAccountsCheck implements RepoCheck {

    private final FreshAccountsProperties properties;

    public FreshAccountsCheck(FreshAccountsProperties properties) {
        this.properties = properties;
    }

    @Override
    public String id() {
        return CheckIds.FRESH_ACCOUNTS;
    }

    @Override
    public CheckResult evaluate(RepoSnapshot snapshot) {
        List<ActorProfile> actors = snapshot.starActorProfiles();
        boolean history = snapshot.data().actorHistory();
        Instant reference = snapshot.window().to();
        Instant newAccountSince = reference.minus(Duration.ofDays(properties.newAccountDays()));
        Instant emptySince = reference.minus(Duration.ofDays(properties.emptyActorDays()));

        int withData = 0;
        int newAccounts = 0;
        int emptyAccounts = 0;
        List<String> freshHashes = new ArrayList<>();
        for (ActorProfile actor : actors) {
            Instant bornAt = bornAt(actor, history);
            boolean isNew = isNew(bornAt, newAccountSince);
            boolean isEmpty = isEmpty(actor, history, emptySince);

            if (bornAt != null || history) {
                withData++;
            }
            if (isNew) {
                newAccounts++;
            }
            if (isEmpty) {
                emptyAccounts++;
            }
            if (isNew || isEmpty) {
                freshHashes.add(actor.actorHash());
            }
        }

        int sample = actors.size();
        int fresh = freshHashes.size();
        double freshShare = (double) fresh / Math.max(sample, 1);
        double s2 = Math.clamp((freshShare - properties.shareFloor()) / properties.shareSpan(), 0.0, 1.0);
        double gate = Math.min(1.0, (double) sample / properties.minActors());
        double score = s2 * gate;
        double coverage = (double) withData / Math.max(sample, 1);
        double confidence = properties.confidence() * coverage;

        Map<String, Object> details = new HashMap<>();
        details.put("mocked", false);
        details.put("actor_history", history);
        details.put("star_actors", sample);
        details.put("actors_with_data", withData);
        details.put("coverage", round3(coverage));
        details.put("fresh_actors", fresh);
        details.put("new_accounts", newAccounts);
        details.put("empty_accounts", emptyAccounts);
        details.put("fresh_share", round3(freshShare));
        details.put("s2", round3(s2));
        details.put("gate", round3(gate));
        details.put("fresh_actor_sample", freshHashes.stream()
                .sorted()
                .limit(properties.sampleHashes())
                .toList());
        details.put("new_account_days", properties.newAccountDays());
        details.put("empty_actor_days", properties.emptyActorDays());
        details.put("share_floor", properties.shareFloor());
        details.put("share_span", properties.shareSpan());
        details.put("min_actors", properties.minActors());
        if (sample == 0) {
            details.put("reason", "no_star_actors");
        } else if (withData == 0) {
            details.put("reason", "no_actor_data");
        } else if (withData < sample) {
            details.put("reason", "partial_actor_data");
        }
        return new CheckResult(id(), score, confidence, details);
    }

    /**
     * Хэши акторов, которых C2 считает свежими или пустыми (для разметки таймлайна звёзд).
     * Сопоставляются с актором через {@link ActorProfile#hashOf(long)}.
     */
    public Set<String> freshActorHashes(RepoSnapshot snapshot) {
        boolean history = snapshot.data().actorHistory();
        Instant reference = snapshot.window().to();
        Instant newAccountSince = reference.minus(Duration.ofDays(properties.newAccountDays()));
        Instant emptySince = reference.minus(Duration.ofDays(properties.emptyActorDays()));
        Set<String> fresh = new HashSet<>();
        for (ActorProfile actor : snapshot.starActorProfiles()) {
            if (isNew(bornAt(actor, history), newAccountSince) || isEmpty(actor, history, emptySince)) {
                fresh.add(actor.actorHash());
            }
        }
        return fresh;
    }

    private static Instant bornAt(ActorProfile actor, boolean history) {
        if (actor.accountCreatedAt() != null) {
            return actor.accountCreatedAt();
        }
        return history ? actor.firstSeen() : null;
    }

    private static boolean isNew(Instant bornAt, Instant newAccountSince) {
        return bornAt != null && bornAt.isAfter(newAccountSince);
    }

    private static boolean isEmpty(ActorProfile actor, boolean history, Instant emptySince) {
        return history
                && (actor.lastActivityAt() == null || actor.lastActivityAt().isBefore(emptySince));
    }

    private static double round3(double value) {
        return Math.round(value * 1000.0) / 1000.0;
    }
}
