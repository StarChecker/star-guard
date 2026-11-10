package ru.itmo.ipkn.starguard.core.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import ru.itmo.ipkn.starguard.core.archive.GhArchiveBulkReader;
import ru.itmo.ipkn.starguard.core.archive.GhArchiveFeignClient;
import ru.itmo.ipkn.starguard.core.archive.GhArchiveParser;
import ru.itmo.ipkn.starguard.core.archive.HttpGhArchiveBulkReader;
import ru.itmo.ipkn.starguard.core.archive.TimeWindowCalculator;
import ru.itmo.ipkn.starguard.core.index.IndexRepoDataProvider;
import ru.itmo.ipkn.starguard.core.index.RepoIndex;
import ru.itmo.ipkn.starguard.core.index.RepoIndexBuilder;
import ru.itmo.ipkn.starguard.core.index.RepoIndexLifecycle;
import ru.itmo.ipkn.starguard.core.service.RepoDataProvider;

import java.time.Clock;

/**
 * Индекс активен только в HTTP-режиме и при {@code app.index.enabled=true}.
 * В fixture-режиме и при отключённом индексе работает прежний прямой путь.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnExpression("${app.index.enabled:true} && '${app.archive.mode:http}' == 'http'")
@EnableScheduling
public class IndexConfig {

    @Bean
    public RepoIndex repoIndex() {
        return new RepoIndex();
    }

    @Bean
    public GhArchiveBulkReader ghArchiveBulkReader(
            GhArchiveFeignClient feignClient, GhArchiveParser parser) {
        return new HttpGhArchiveBulkReader(feignClient, parser);
    }

    @Bean
    public RepoIndexBuilder repoIndexBuilder(
            GhArchiveBulkReader bulkReader,
            TimeWindowCalculator windowCalculator,
            ArchiveProperties archiveProperties,
            RepoIndexProperties indexProperties,
            Clock clock) {
        return new RepoIndexBuilder(
                bulkReader, windowCalculator, archiveProperties, indexProperties, clock);
    }

    @Bean
    public RepoIndexLifecycle repoIndexLifecycle(
            RepoIndexBuilder builder,
            RepoIndex repoIndex,
            RepoIndexProperties properties) {
        return new RepoIndexLifecycle(builder, repoIndex, properties);
    }

    @Bean
    public RepoDataProvider indexRepoDataProvider(
            RepoIndex repoIndex,
            ArchiveProperties archiveProperties,
            Clock clock) {
        return new IndexRepoDataProvider(repoIndex, archiveProperties, clock);
    }
}
