package ru.itmo.ipkn.starguard.core.github;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

/** Ответ GitHub REST {@code GET /repos/{owner}/{repo}} — только нужные поля. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GithubRepoInfo(
        @JsonProperty("full_name") String fullName,
        @JsonProperty("description") String description,
        @JsonProperty("html_url") String htmlUrl,
        @JsonProperty("stargazers_count") Integer stargazersCount,
        @JsonProperty("forks_count") Integer forksCount,
        @JsonProperty("open_issues_count") Integer openIssuesCount,
        @JsonProperty("subscribers_count") Integer subscribersCount,
        @JsonProperty("archived") Boolean archived,
        @JsonProperty("fork") Boolean fork,
        @JsonProperty("created_at") Instant createdAt,
        @JsonProperty("pushed_at") Instant pushedAt
) {
}
