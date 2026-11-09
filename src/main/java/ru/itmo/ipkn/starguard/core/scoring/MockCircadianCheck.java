package ru.itmo.ipkn.starguard.core.scoring;

import org.springframework.stereotype.Component;
import ru.itmo.ipkn.starguard.core.config.ScoringProperties;

@Component
public class MockCircadianCheck extends AbstractMockCheck {

    public MockCircadianCheck(ScoringProperties properties, MockModeContext context) {
        super(properties, context);
    }

    @Override
    public String id() {
        return CheckIds.CIRCADIAN;
    }
}
