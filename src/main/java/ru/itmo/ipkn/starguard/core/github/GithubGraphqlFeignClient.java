package ru.itmo.ipkn.starguard.core.github;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Map;

/** GitHub GraphQL API: история звёзд репозитория и пакетная проверка существования. Нужен токен. */
@FeignClient(
        name = "githubGraphql",
        url = "${app.github.base-url:https://api.github.com}",
        configuration = GithubFeignConfig.class)
public interface GithubGraphqlFeignClient {

    @PostMapping(path = "/graphql", consumes = "application/json")
    Map<String, Object> query(@RequestBody Map<String, Object> body);
}
