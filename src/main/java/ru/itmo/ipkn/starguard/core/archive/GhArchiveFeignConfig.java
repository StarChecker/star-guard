package ru.itmo.ipkn.starguard.core.archive;

import feign.Retryer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Конфигурация Feign для GH Archive: встроенные ретраи выключены,
 * потому что 404 означает «час ещё не опубликован», а ретраи контролирует
 * {@link HttpGhArchiveClient}.
 */
@Configuration(proxyBeanMethods = false)
public class GhArchiveFeignConfig {

    @Bean
    public Retryer feignRetryer() {
        return Retryer.NEVER_RETRY;
    }
}
