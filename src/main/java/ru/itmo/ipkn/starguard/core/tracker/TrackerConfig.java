package ru.itmo.ipkn.starguard.core.tracker;

import com.zaxxer.hikari.HikariConfig;
import org.springframework.beans.factory.annotation.Qualifier;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.transaction.support.TransactionTemplate;
import ru.itmo.ipkn.starguard.core.config.GithubProperties;
import ru.itmo.ipkn.starguard.core.config.TrackerProperties;
import ru.itmo.ipkn.starguard.core.github.GithubTrackerClient;

import java.time.Clock;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = TrackerProperties.ENABLED_PROPERTY, havingValue = "true")
public class TrackerConfig {

    @Bean(destroyMethod = "close")
    HikariDataSource trackerDataSource(TrackerProperties properties) {
        HikariConfig config = new HikariConfig();
        config.setPoolName("star-tracker");
        config.setJdbcUrl(properties.jdbcUrl());
        config.setUsername(properties.username());
        config.setPassword(properties.password());
        config.setMaximumPoolSize(properties.poolSize());
        HikariDataSource dataSource = new HikariDataSource(config);
        new ResourceDatabasePopulator(new ClassPathResource("tracker/schema.sql")).execute(dataSource);
        return dataSource;
    }

    @Bean
    TrackerStore trackerStore(HikariDataSource trackerDataSource) {
        return new TrackerStore(new JdbcTemplate(trackerDataSource),
                new TransactionTemplate(new DataSourceTransactionManager(trackerDataSource)));
    }

    @Bean
    StarTracker starTracker(
            TrackerStore trackerStore,
            GithubTrackerClient client,
            TrackerProperties properties,
            GithubProperties githubProperties,
            Clock clock) {
        return new StarTracker(trackerStore, client, properties, githubProperties, clock);
    }

    @Bean(destroyMethod = "shutdown")
    ThreadPoolTaskScheduler trackerScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(2);
        scheduler.setThreadNamePrefix("star-tracker-");
        scheduler.setDaemon(true);
        scheduler.initialize();
        return scheduler;
    }

    @Bean
    TrackerLifecycle trackerLifecycle(
            StarTracker starTracker,
            @Qualifier("trackerScheduler") ThreadPoolTaskScheduler trackerScheduler,
            TrackerProperties properties) {
        return new TrackerLifecycle(starTracker, trackerScheduler, properties);
    }
}
