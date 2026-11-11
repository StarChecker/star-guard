package ru.itmo.ipkn.starguard.core.github;

import feign.RequestInterceptor;
import org.springframework.context.annotation.Bean;
import ru.itmo.ipkn.starguard.core.config.GithubProperties;

public class GithubFeignConfig {

    @Bean
    public RequestInterceptor githubHeaders(GithubProperties properties) {
        return template -> {
            template.header("Accept", "application/vnd.github+json");
            template.header("X-GitHub-Api-Version", "2022-11-28");
            template.header("User-Agent", "star-guard");
            if (!properties.token().isEmpty()) {
                template.header("Authorization", "Bearer " + properties.token());
            }
        };
    }
}
