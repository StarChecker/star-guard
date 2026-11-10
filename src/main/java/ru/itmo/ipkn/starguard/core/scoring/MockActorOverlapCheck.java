package ru.itmo.ipkn.starguard.core.scoring;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import ru.itmo.ipkn.starguard.core.config.ActorOverlapProperties;
import ru.itmo.ipkn.starguard.core.config.ScoringProperties;

/** Мок C4; заменяется на {@link ActorOverlapCheck} при {@code app.scoring.checks.actor-overlap.mode=real}. */
@Component
@ConditionalOnProperty(name = ActorOverlapProperties.MODE_PROPERTY, havingValue = "mock", matchIfMissing = true)
public class MockActorOverlapCheck extends AbstractMockCheck {

    public MockActorOverlapCheck(ScoringProperties properties, MockModeContext context) {
        super(properties, context);
    }

    @Override
    public String id() {
        return CheckIds.ACTOR_OVERLAP;
    }
}
