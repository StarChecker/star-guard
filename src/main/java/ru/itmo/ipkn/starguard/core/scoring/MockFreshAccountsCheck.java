package ru.itmo.ipkn.starguard.core.scoring;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import ru.itmo.ipkn.starguard.core.config.FreshAccountsProperties;
import ru.itmo.ipkn.starguard.core.config.ScoringProperties;

@Component
@ConditionalOnProperty(name = FreshAccountsProperties.MODE_PROPERTY, havingValue = "mock", matchIfMissing = true)
public class MockFreshAccountsCheck extends AbstractMockCheck {

    public MockFreshAccountsCheck(ScoringProperties properties, MockModeContext context) {
        super(properties, context);
    }

    @Override
    public String id() {
        return CheckIds.FRESH_ACCOUNTS;
    }
}
