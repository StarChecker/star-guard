package ru.itmo.ipkn.starguard.core.github;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Map;

/** GitHub REST API: карточка репозитория и число контрибьюторов для preview (F5). Токен необязателен. */
@FeignClient(
        name = "githubRepos",
        url = "${app.github.base-url:https://api.github.com}",
        configuration = GithubFeignConfig.class)
public interface GithubReposFeignClient {

    @GetMapping("/repos/{owner}/{name}")
    GithubRepoInfo repo(@PathVariable("owner") String owner, @PathVariable("name") String name);

    /**
     * Одна запись на страницу: число контрибьюторов = номер последней страницы из заголовка Link.
     */
    @GetMapping("/repos/{owner}/{name}/contributors")
    ResponseEntity<List<Map<String, Object>>> contributors(
            @PathVariable("owner") String owner,
            @PathVariable("name") String name,
            @RequestParam("per_page") int perPage,
            @RequestParam("anon") String anon);
}
