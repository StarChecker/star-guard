package ru.itmo.ipkn.starguard.core.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import ru.itmo.ipkn.starguard.core.scoring.CheckMode;

@ConfigurationProperties(prefix = "app.scoring.checks.fresh-accounts")
public record FreshAccountsProperties(
        CheckMode mode,
        Integer newAccountDays,
        Integer emptyActorDays,
        Double shareFloor,
        Double shareSpan,
        Integer minActors,
        Double confidence,
        Integer sampleHashes
) {

    public static final String MODE_PROPERTY = "app.scoring.checks.fresh-accounts.mode";

    public FreshAccountsProperties {
        mode = mode == null ? CheckMode.MOCK : mode;
        newAccountDays = newAccountDays == null ? 30 : newAccountDays;
        emptyActorDays = emptyActorDays == null ? 90 : emptyActorDays;
        shareFloor = shareFloor == null ? 0.3 : shareFloor;
        shareSpan = shareSpan == null ? 0.6 : shareSpan;
        minActors = minActors == null ? 10 : minActors;
        confidence = confidence == null ? 0.9 : confidence;
        sampleHashes = sampleHashes == null ? 5 : sampleHashes;

        if (newAccountDays < 1) {
            throw new IllegalArgumentException("app.scoring.checks.fresh-accounts.new-account-days must be >= 1");
        }
        if (emptyActorDays < 1) {
            throw new IllegalArgumentException("app.scoring.checks.fresh-accounts.empty-actor-days must be >= 1");
        }
        if (shareFloor < 0.0 || shareFloor >= 1.0) {
            throw new IllegalArgumentException("app.scoring.checks.fresh-accounts.share-floor must be within [0, 1)");
        }
        if (shareSpan <= 0.0) {
            throw new IllegalArgumentException("app.scoring.checks.fresh-accounts.share-span must be > 0");
        }
        if (minActors < 1) {
            throw new IllegalArgumentException("app.scoring.checks.fresh-accounts.min-actors must be >= 1");
        }
        if (confidence < 0.0 || confidence > 1.0) {
            throw new IllegalArgumentException("app.scoring.checks.fresh-accounts.confidence must be within [0, 1]");
        }
        if (sampleHashes < 0) {
            throw new IllegalArgumentException("app.scoring.checks.fresh-accounts.sample-hashes must be >= 0");
        }
    }

    public static FreshAccountsProperties defaults() {
        return new FreshAccountsProperties(null, null, null, null, null, null, null, null);
    }
}
