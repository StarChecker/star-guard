package ru.itmo.ipkn.starguard.core.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import ru.itmo.ipkn.starguard.core.scoring.BurstCheck;
import ru.itmo.ipkn.starguard.core.scoring.MockBurstCheck;
import ru.itmo.ipkn.starguard.core.scoring.MockModeContext;

/** Подключает C1 только когда проверка включена, с выбранной реализацией. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = BurstProperties.ENABLED_PROPERTY, havingValue = "true")
public class BurstCheckConfiguration {

    @Bean
    @ConditionalOnProperty(name = BurstProperties.MODE_PROPERTY, havingValue = "real")
    BurstCheck burstCheck(BurstProperties properties) {
        return new BurstCheck(properties);
    }

    @Bean
    @ConditionalOnProperty(name = BurstProperties.MODE_PROPERTY, havingValue = "mock")
    MockBurstCheck mockBurstCheck(ScoringProperties properties, MockModeContext context) {
        return new MockBurstCheck(properties, context);
    }
}
