package ru.itmo.ipkn.starguard.core.pool;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.env.Environment;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.transaction.support.TransactionTemplate;
import ru.itmo.ipkn.starguard.core.config.AccountPoolProperties;
import ru.itmo.ipkn.starguard.core.config.GithubProperties;
import ru.itmo.ipkn.starguard.core.config.ScoringProperties;
import ru.itmo.ipkn.starguard.core.github.GithubAccountPoolClient;
import java.time.Clock;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = {"app.account-pool.enabled", "app.tracker.enabled"}, havingValue = "true")
public class AccountPoolConfig {
    @Bean
    AccountPoolReports accountPoolReports(AccountPoolStore store, AccountPoolProperties p, Clock clock,
            ScoringProperties scoring, Environment environment) {
        return new AccountPoolReports(store, p, clock, scoring, environment);
    }
    @Bean
    AccountPoolStore accountPoolStore(HikariDataSource trackerDataSource) {
        return new AccountPoolStore(new JdbcTemplate(trackerDataSource),
                new TransactionTemplate(new DataSourceTransactionManager(trackerDataSource)));
    }
    @Bean(destroyMethod = "shutdown")
    ThreadPoolTaskScheduler accountPoolScheduler() {
        var scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("account-pool-");
        scheduler.setDaemon(true);
        scheduler.initialize();
        return scheduler;
    }
    @Bean
    AccountPoolCollector accountPoolCollector(AccountPoolStore store, GithubAccountPoolClient client,
            AccountPoolProperties p, GithubProperties github,
            @Qualifier("accountPoolScheduler") ThreadPoolTaskScheduler accountPoolScheduler,
            Clock clock) {
        return new AccountPoolCollector(store, client, p, github, accountPoolScheduler, clock);
    }
}
