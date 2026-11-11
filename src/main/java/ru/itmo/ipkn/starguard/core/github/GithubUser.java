package ru.itmo.ipkn.starguard.core.github;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

public record GithubUser(
        @JsonProperty("login") String login,
        @JsonProperty("created_at") Instant createdAt
) {
}
