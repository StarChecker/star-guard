package ru.itmo.ipkn.starguard.core.scoring;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import ru.itmo.ipkn.starguard.core.config.EngagementProperties;
import ru.itmo.ipkn.starguard.core.config.ScoringProperties;

/** Мок C3; заменяется на {@link EngagementCheck} при {@code app.scoring.checks.engagement.mode=real}. */
@Component
@ConditionalOnProperty(name = EngagementProperties.MODE_PROPERTY, havingValue = "mock", matchIfMissing = true)
public class MockEngagementCheck extends AbstractMockCheck {

    public MockEngagementCheck(ScoringProperties properties, MockModeContext context) {
        super(properties, context);
    }

    @Override
    public String id() {
        return CheckIds.ENGAGEMENT;
    }
}
