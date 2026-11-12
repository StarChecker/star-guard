package ru.itmo.ipkn.starguard.core.github;

import feign.FeignException;
import org.springframework.stereotype.Service;
import ru.itmo.ipkn.starguard.core.archive.ArchiveFetchResult;
import ru.itmo.ipkn.starguard.core.archive.ArchiveProgress;
import ru.itmo.ipkn.starguard.core.config.ArchiveProperties;
import ru.itmo.ipkn.starguard.core.config.GithubHistoryProperties;
import ru.itmo.ipkn.starguard.core.config.GithubProperties;
import ru.itmo.ipkn.starguard.core.domain.DataSource;
import ru.itmo.ipkn.starguard.core.domain.EventType;
import ru.itmo.ipkn.starguard.core.domain.GhEvent;
import ru.itmo.ipkn.starguard.core.domain.RepoRef;
import ru.itmo.ipkn.starguard.core.domain.StarIndex;
import ru.itmo.ipkn.starguard.core.domain.StarMark;
import ru.itmo.ipkn.starguard.core.domain.TimeWindow;
import ru.itmo.ipkn.starguard.core.service.RepoData;
import ru.itmo.ipkn.starguard.core.service.RepoNotFoundException;
import ru.itmo.ipkn.starguard.core.stats.RepoAggregate;
import ru.itmo.ipkn.starguard.core.stats.StatsAggregator;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Анализ любого репозитория по истории GitHub, без GH Archive: последние звёзды со временем
 * и возрастом аккаунтов, форки, issues, PR и коммиты из GraphQL API.
 * <p>
 * Окно — от момента, с которого все загруженные списки полные, до текущего часа,
 * но не глубже {@code max-days}. Так C3 не сравнивает звёзды за месяц с активностью за день.
 * C4 в этом режиме неактивна: нет индекса звёзд по другим репозиториям.
 */
@Service
public class GithubHistoryService {

    private static final int PAGE = 100;

    private static final String STARS_QUERY = """
            query($owner: String!, $name: String!, $before: String) {
              repository(owner: $owner, name: $name) {
                stargazerCount
                stargazers(last: 100, before: $before, orderBy: {field: STARRED_AT, direction: ASC}) {
                  pageInfo { hasPreviousPage startCursor }
                  edges { starredAt node { databaseId login createdAt } }
                }
              }
            }
            """;

    private static final String ACTIVITY_QUERY = """
            query($owner: String!, $name: String!, $since: GitTimestamp!) {
              repository(owner: $owner, name: $name) {
                forks(last: 100, orderBy: {field: CREATED_AT, direction: ASC}) {
                  pageInfo { hasPreviousPage }
                  nodes { createdAt owner { login ... on User { databaseId } ... on Organization { databaseId } } }
                }
                issues(last: 100, orderBy: {field: CREATED_AT, direction: ASC}) {
                  pageInfo { hasPreviousPage }
                  nodes { createdAt author { login ... on User { databaseId } } }
                }
                pullRequests(last: 100, orderBy: {field: CREATED_AT, direction: ASC}) {
                  pageInfo { hasPreviousPage }
                  nodes { createdAt author { login ... on User { databaseId } } }
                }
                defaultBranchRef {
                  target {
                    ... on Commit {
                      history(first: 100, since: $since) {
                        pageInfo { hasNextPage }
                        nodes { committedDate author { user { databaseId login } } }
                      }
                    }
                  }
                }
              }
            }
            """;

    private final GithubGraphqlFeignClient client;
    private final GithubProperties githubProperties;
    private final GithubHistoryProperties properties;
    private final ArchiveProperties archiveProperties;
    private final StatsAggregator aggregator;
    private final Clock clock;

    public GithubHistoryService(
            GithubGraphqlFeignClient client,
            GithubProperties githubProperties,
            GithubHistoryProperties properties,
            ArchiveProperties archiveProperties,
            StatsAggregator aggregator,
            Clock clock) {
        this.client = client;
        this.githubProperties = githubProperties;
        this.properties = properties;
        this.archiveProperties = archiveProperties;
        this.aggregator = aggregator;
        this.clock = clock;
    }

    /** Есть токен и это не офлайн-режим фикстур. */
    public boolean available() {
        return !githubProperties.token().isEmpty()
                && archiveProperties.mode() != ArchiveProperties.Mode.FIXTURE;
    }

    public RepoData load(RepoRef repo, ArchiveProgress progress) {
        if (githubProperties.token().isEmpty()) {
            throw new GithubUnavailableException("GITHUB_TOKEN_REQUIRED",
                    "GitHub token is required for repository history (set GITHUB_TOKEN)");
        }
        Instant now = clock.instant();
        Instant cutoff = now.minus(Duration.ofDays(properties.maxDays())).truncatedTo(ChronoUnit.HOURS);
        Instant to = now.truncatedTo(ChronoUnit.HOURS).plus(Duration.ofHours(1));
        TimeWindow fullWindow = new TimeWindow(cutoff, to, (int) Duration.between(cutoff, to).toHours());

        // 1. Последние звёзды: страницы по 100 от новых к старым.
        List<Map<String, Object>> starEdges = new ArrayList<>();
        String before = null;
        long stargazerCount = 0;
        int pagesPlanned = 1;
        int pagesDone = 0;
        boolean starsComplete = true;
        while (true) {
            Map<String, Object> repository = repository(repo, STARS_QUERY, vars(repo, "before", before));
            stargazerCount = Graphql.number(repository, "stargazerCount", 0);
            pagesPlanned = (int) Math.max(1, Math.ceil(Math.min(stargazerCount, properties.maxStars()) / (double) PAGE)) + 1;
            Map<String, Object> stargazers = Graphql.map(repository, "stargazers");
            List<Object> edges = Graphql.list(stargazers, "edges");
            if (before == null && edges.isEmpty() && stargazerCount > 0) {
                throw new StarsHiddenException(repo, stargazerCount);
            }
            for (int i = edges.size() - 1; i >= 0; i--) {
                if (edges.get(i) instanceof Map<?, ?> edge) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> typed = (Map<String, Object>) edge;
                    starEdges.add(typed);
                }
            }
            progress.hourDone(++pagesDone, pagesPlanned);
            Map<String, Object> pageInfo = Graphql.map(stargazers, "pageInfo");
            boolean more = Graphql.bool(pageInfo, "hasPreviousPage");
            Instant oldest = edges.isEmpty() ? null : Graphql.instant(asMap(edges.getFirst()), "starredAt");
            if (!more) {
                break;
            }
            if (oldest != null && oldest.isBefore(cutoff)) {
                break;
            }
            if (starEdges.size() >= properties.maxStars()) {
                starsComplete = false;
                break;
            }
            before = Graphql.string(pageInfo, "startCursor");
        }
        if (starEdges.isEmpty()) {
            throw new RepoNotFoundException(repo, fullWindow);
        }

        // 2. Остальная активность — одним запросом, по последним 100 каждого вида.
        Map<String, Object> activity = repository(repo, ACTIVITY_QUERY, vars(repo, "since", cutoff.toString()));
        progress.hourDone(pagesPlanned, pagesPlanned);

        // 3. Окно: с момента, когда все загруженные списки полные.
        Instant start = cutoff;
        Instant oldestStar = starEdges.stream()
                .map(edge -> Graphql.instant(edge, "starredAt"))
                .filter(Objects::nonNull)
                .min(Comparator.naturalOrder())
                .orElse(cutoff);
        if (!starsComplete) {
            // загрузили не все звёзды окна — анализируем только покрытый отрезок
            start = later(start, oldestStar);
        }
        List<GhEvent> events = new ArrayList<>();
        start = later(start, collect(activity, "forks", "owner", EventType.FORK, repo, events));
        start = later(start, collect(activity, "issues", "author", EventType.ISSUES, repo, events));
        start = later(start, collect(activity, "pullRequests", "author", EventType.PULL_REQUEST, repo, events));
        start = later(start, commits(activity, repo, events));

        Instant from = start.truncatedTo(ChronoUnit.HOURS);
        int hours = (int) Math.max(1, Duration.between(from, to).toHours());
        TimeWindow window = new TimeWindow(from, to, hours);

        List<StarMark> marks = new ArrayList<>();
        int index = 0;
        for (Map<String, Object> edge : starEdges) {
            Instant at = Graphql.instant(edge, "starredAt");
            if (at == null || at.isBefore(from) || !at.isBefore(to)) {
                continue;
            }
            Map<String, Object> user = Graphql.map(edge, "node");
            String login = loginOf(user);
            long actorId = actorId(user, login);
            events.add(new GhEvent("gh-star-" + index++, EventType.WATCH, 0, repo.fullName(),
                    actorId, login, at, Graphql.instant(user, "createdAt")));
            marks.add(new StarMark(at, actorId, false));
        }
        List<GhEvent> inWindow = events.stream()
                .filter(event -> !event.createdAt().isBefore(from) && event.createdAt().isBefore(to))
                .sorted(Comparator.comparing(GhEvent::createdAt))
                .toList();
        if (marks.isEmpty()) {
            throw new RepoNotFoundException(repo, window);
        }

        RepoAggregate aggregate = aggregator.aggregate(repo, window, inWindow, StarIndex.empty());
        ArchiveFetchResult archive = new ArchiveFetchResult(
                inWindow, hours, hours, List.of(), 0, false, StarIndex.empty());
        DataSource source = new DataSource(DataSource.Type.GITHUB_API,
                marks.size(), (int) Math.min(Integer.MAX_VALUE, stargazerCount), from);
        return new RepoData(window, aggregate, archive, null, marks, source);
    }

    private Map<String, Object> repository(RepoRef repo, String query, Map<String, Object> variables) {
        Map<String, Object> response;
        try {
            response = client.query(Map.of("query", query, "variables", variables));
        } catch (FeignException e) {
            if (e.status() == 401) {
                throw new GithubUnavailableException("GITHUB_TOKEN_REQUIRED", "GitHub rejected the token", e);
            }
            throw new GithubUnavailableException("GITHUB_UNAVAILABLE",
                    "GitHub API request failed: HTTP " + e.status(), e);
        } catch (RuntimeException e) {
            throw new GithubUnavailableException("GITHUB_UNAVAILABLE", "GitHub API request failed", e);
        }
        Map<String, Object> repository = Graphql.map(response, "data", "repository");
        if (repository == null) {
            if (Graphql.notFound(response) || Graphql.map(response, "data") != null) {
                throw new RepoGoneException(repo);
            }
            throw new GithubUnavailableException("GITHUB_UNAVAILABLE", "GitHub API returned no data");
        }
        return repository;
    }

    private static Map<String, Object> vars(RepoRef repo, String key, String value) {
        Map<String, Object> variables = new HashMap<>();
        variables.put("owner", repo.owner());
        variables.put("name", repo.name());
        variables.put(key, value);
        return variables;
    }

    /** Добавляет события списка и возвращает начало его полного покрытия (или null, если полон весь). */
    private static Instant collect(
            Map<String, Object> repository, String connection, String actorField,
            EventType type, RepoRef repo, List<GhEvent> out) {
        Map<String, Object> list = Graphql.map(repository, connection);
        List<Object> nodes = Graphql.list(list, "nodes");
        Instant oldest = null;
        int i = 0;
        for (Object raw : nodes) {
            Map<String, Object> node = asMap(raw);
            Instant at = Graphql.instant(node, "createdAt");
            if (at == null) {
                continue;
            }
            Map<String, Object> actor = Graphql.map(node, actorField);
            String login = loginOf(actor);
            out.add(new GhEvent("gh-" + connection + "-" + i++, type, 0, repo.fullName(),
                    actorId(actor, login), login, at, null));
            oldest = oldest == null || at.isBefore(oldest) ? at : oldest;
        }
        boolean truncated = Graphql.bool(Graphql.map(list, "pageInfo"), "hasPreviousPage");
        return truncated ? oldest : null;
    }

    /** Коммиты → пуши: один пуш на автора в час, чтобы серия коммитов не считалась десятками действий. */
    private static Instant commits(Map<String, Object> repository, RepoRef repo, List<GhEvent> out) {
        Map<String, Object> history = Graphql.map(repository, "defaultBranchRef", "target", "history");
        List<Object> nodes = Graphql.list(history, "nodes");
        Set<String> seen = new HashSet<>();
        Instant oldest = null;
        int i = 0;
        for (Object raw : nodes) {
            Map<String, Object> node = asMap(raw);
            Instant at = Graphql.instant(node, "committedDate");
            if (at == null) {
                continue;
            }
            oldest = oldest == null || at.isBefore(oldest) ? at : oldest;
            Map<String, Object> user = Graphql.map(node, "author", "user");
            String login = loginOf(user);
            long actorId = actorId(user, login);
            if (seen.add(actorId + "|" + at.truncatedTo(ChronoUnit.HOURS))) {
                out.add(new GhEvent("gh-push-" + i++, EventType.PUSH, 0, repo.fullName(),
                        actorId, login, at, null));
            }
        }
        boolean truncated = Graphql.bool(Graphql.map(history, "pageInfo"), "hasNextPage");
        return truncated ? oldest : null;
    }

    private static String loginOf(Map<String, Object> actor) {
        String login = Graphql.string(actor, "login");
        return login == null ? "" : login;
    }

    /** databaseId, а для ботов и удалённых аккаунтов — стабильный отрицательный id по логину. */
    private static long actorId(Map<String, Object> actor, String login) {
        long id = Graphql.number(actor, "databaseId", 0);
        if (id > 0) {
            return id;
        }
        return -1L - Math.abs((long) (login.isEmpty() ? "ghost" : login).hashCode());
    }

    private static Instant later(Instant current, Instant candidate) {
        return candidate != null && candidate.isAfter(current) ? candidate : current;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object raw) {
        return raw instanceof Map<?, ?> ? (Map<String, Object>) raw : Map.of();
    }
}
