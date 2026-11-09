package ru.itmo.ipkn.starguard.core.scoring;

import org.springframework.stereotype.Component;
import ru.itmo.ipkn.starguard.core.config.ScoringProperties;

@Component
public class MockBurstCheck extends AbstractMockCheck {

    public MockBurstCheck(ScoringProperties properties, MockModeContext context) {
        super(properties, context);
    }

    @Override
    public String id() {
        return CheckIds.BURST;
    }
}
