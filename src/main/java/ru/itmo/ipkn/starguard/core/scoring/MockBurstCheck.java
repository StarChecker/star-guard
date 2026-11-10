package ru.itmo.ipkn.starguard.core.scoring;

import ru.itmo.ipkn.starguard.core.config.ScoringProperties;

public class MockBurstCheck extends AbstractMockCheck {

    public MockBurstCheck(ScoringProperties properties, MockModeContext context) {
        super(properties, context);
    }

    @Override
    public String id() {
        return CheckIds.BURST;
    }
}
