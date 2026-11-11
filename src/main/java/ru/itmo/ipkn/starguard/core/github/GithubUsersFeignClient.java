package ru.itmo.ipkn.starguard.core.github;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(
        name = "githubApi",
        url = "${app.github.base-url:https://api.github.com}",
        configuration = GithubFeignConfig.class)
public interface GithubUsersFeignClient {

    @GetMapping("/users/{login}")
    GithubUser user(@PathVariable("login") String login);
}
