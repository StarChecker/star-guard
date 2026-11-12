package ru.itmo.ipkn.starguard.core.service;

import ru.itmo.ipkn.starguard.core.domain.GithubSignals;
import ru.itmo.ipkn.starguard.core.domain.RepoRef;

import java.time.Instant;
import java.util.Optional;

public interface IntradaySource {

    void watch(String nodeId, RepoRef repo, Instant createdAt, long stars, long forks);

    Optional<GithubSignals.Intraday> intraday(String nodeId);
}
