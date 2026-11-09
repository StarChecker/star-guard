package ru.itmo.ipkn.starguard.core.scoring;

import org.springframework.stereotype.Component;
import ru.itmo.ipkn.starguard.core.config.ScoringProperties;

@Component
public class MockEngagementCheck extends AbstractMockCheck {

    public MockEngagementCheck(ScoringProperties properties, MockModeContext context) {
        super(properties, context);
    }

    @Override
    public String id() {
        return CheckIds.ENGAGEMENT;
    }
}
