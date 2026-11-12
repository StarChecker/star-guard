package ru.itmo.ipkn.starguard.core.github;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import feign.FeignException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import ru.itmo.ipkn.starguard.core.archive.ArchiveFetchResult;
import ru.itmo.ipkn.starguard.core.archive.ArchiveProgress;
import ru.itmo.ipkn.starguard.core.archive.InvalidWindowException;
import ru.itmo.ipkn.starguard.core.config.ArchiveProperties;
import ru.itmo.ipkn.starguard.core.config.GithubHistoryProperties;
import ru.itmo.ipkn.starguard.core.config.GithubProperties;
import ru.itmo.ipkn.starguard.core.domain.DataAvailability;
import ru.itmo.ipkn.starguard.core.domain.DataSource;
import ru.itmo.ipkn.starguard.core.domain.EventCounts;
import ru.itmo.ipkn.starguard.core.domain.GithubSignals;
import ru.itmo.ipkn.starguard.core.domain.RepoRef;
import ru.itmo.ipkn.starguard.core.domain.RepoSnapshot;
import ru.itmo.ipkn.starguard.core.domain.RepoStats;
import ru.itmo.ipkn.starguard.core.domain.StarIndex;
import ru.itmo.ipkn.starguard.core.domain.TimeWindow;
import ru.itmo.ipkn.starguard.core.service.IntradaySource;
import ru.itmo.ipkn.starguard.core.service.RepoData;
import ru.itmo.ipkn.starguard.core.stats.RepoAggregate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class GithubHistoryService {

    private static final Logger log = LoggerFactory.getLogger(GithubHistoryService.class);
    private static final int MAX_HISTORY_PAGES = 100;
    private static final int DAYS_PER_PAGE = 210;
    /** Окно анализа по умолчанию, если период не задан (совпадает с прежним max-days). */
    private static final int DEFAULT_ANALYSIS_DAYS = 30;
    /** Сколько дней истории до начала окна нужно проверкам для базовой линии. */
    private static final int BASELINE_DAYS = 67;   // 60 (dev-activity) + 7 (min baseline)
    /** Трекер хранит внутридневные снимки ограниченное время — для старых окон их не подмешиваем. */
    private static final Duration INTRADAY_RELEVANCE = Duration.ofHours(30);
    private static final int NOT_FOUND = 404;
    private static final int UNAUTHORIZED = 401;
    private static final int FORBIDDEN = 403;
    private static final int TOO_MANY_REQUESTS = 429;
    private static final long RETRY_DELAY_MILLIS = 500;

    private static final String REPO_QUERY = """
            query($owner: String!, $name: String!, $since: GitTimestamp!, $until: GitTimestamp!) {
              repository(owner: $owner, name: $name) {
                id
                createdAt
                stargazerCount
                forkCount
                forks(first: 100, orderBy: {field: CREATED_AT, direction: DESC}) {
                  totalCount
                  pageInfo { hasNextPage endCursor }
                  nodes { createdAt }
                }
                issues(first: 100, orderBy: {field: CREATED_AT, direction: DESC}) {
                  totalCount
                  pageInfo { hasNextPage endCursor }
                  nodes { createdAt }
                }
                pullRequests(first: 100, orderBy: {field: CREATED_AT, direction: DESC}) {
                  totalCount
                  pageInfo { hasNextPage endCursor }
                  nodes { createdAt }
                }
                defaultBranchRef {
                  target {
                    ... on Commit {
                      history(since: $since, until: $until, first: 100) {
                        totalCount
                        nodes { author { user { login } } }
                      }
                    }
                  }
                }
              }
            }
            """;

    private static final String PAGE_QUERY = """
            query($owner: String!, $name: String!, $after: String) {
              repository(owner: $owner, name: $name) {
                %s(first: 100, after: $after, orderBy: {field: CREATED_AT, direction: DESC}) {
                  pageInfo { hasNextPage endCursor }
                  nodes { createdAt }
                }
              }
            }
            """;

    private final GithubGraphqlFeignClient graphql;
    private final GithubReposFeignClient rest;
    private final GithubProperties githubProperties;
    private final GithubHistoryProperties properties;
    private final ArchiveProperties archiveProperties;
    private final ObjectProvider<IntradaySource> intradaySource;
    private final Clock clock;
    private final Cache<String, GithubSignals> cache;

    public GithubHistoryService(
            GithubGraphqlFeignClient graphql,
            GithubReposFeignClient rest,
            GithubProperties githubProperties,
            GithubHistoryProperties properties,
            ArchiveProperties archiveProperties,
            ObjectProvider<IntradaySource> intradaySource,
            Clock clock) {
        this.graphql = graphql;
        this.rest = rest;
        this.githubProperties = githubProperties;
        this.properties = properties;
        this.archiveProperties = archiveProperties;
        this.intradaySource = intradaySource;
        this.clock = clock;
        this.cache = Caffeine.newBuilder()
                .expireAfterWrite(properties.cacheTtl())
                .maximumSize(10_000)
                .build();
    }

    public boolean available() {
        return !githubProperties.token().isEmpty()
                && archiveProperties.mode() != ArchiveProperties.Mode.FIXTURE;
    }

    public int maxDays() {
        return properties.maxDays();
    }

    public RepoData load(RepoRef repo, ArchiveProgress progress) {
        return load(repo, HistoryPeriod.days(null), progress);
    }

    public RepoData load(RepoRef repo, Integer requestedDays, ArchiveProgress progress) {
        return load(repo, HistoryPeriod.days(requestedDays), progress);
    }

    /**
     * Отчёт по истории GitHub за период: последние {@code days} дней, заканчивающиеся
     * датой {@code to} (включительно), либо вся история репозитория ({@code all}).
     */
    public RepoData load(RepoRef repo, HistoryPeriod requested, ArchiveProgress progress) {
        if (githubProperties.token().isEmpty()) {
            throw new GithubUnavailableException("GITHUB_TOKEN_REQUIRED",
                    "GitHub token is required for repository history (set GITHUB_TOKEN)");
        }
        HistoryPeriod period = requested == null ? HistoryPeriod.days(null) : requested;
        int days = period.all()
                ? 0
                : period.days() == null
                        ? Math.min(properties.maxDays(), DEFAULT_ANALYSIS_DAYS)
                        : period.days();
        if (!period.all() && (days < 1 || days > properties.maxDays())) {
            throw new InvalidWindowException("days must be within [1, %d]".formatted(properties.maxDays()));
        }

        Instant now = clock.instant();
        LocalDate today = now.atZone(ZoneOffset.UTC).toLocalDate();
        LocalDate endDay = period.to() == null ? today : period.to();
        if (endDay.isAfter(today)) {
            throw new InvalidWindowException("to must not be in the future");
        }
        Instant to = endDay.equals(today)
                ? now.truncatedTo(ChronoUnit.HOURS).plus(Duration.ofHours(1))
                : endDay.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant queryFrom = period.all()
                ? Instant.EPOCH
                : endDay.minusDays(days - 1L).atStartOfDay(ZoneOffset.UTC).toInstant();

        String key = repo.fullName().toLowerCase(Locale.ROOT) + "|"
                + (period.all() ? "all" : days) + "|" + endDay + "|" + today;
        GithubSignals signals = cache.getIfPresent(key);
        if (signals == null) {
            signals = fetch(repo, queryFrom, to, endDay, today, period.all() ? null : days, now, progress);
            cache.put(key, signals);
        }
        progress.hourDone(3, 3);

        TimeWindow window = new TimeWindow(
                signals.activity().from(), signals.activity().to(),
                (int) Duration.between(signals.activity().from(), signals.activity().to()).toHours());
        return toRepoData(repo, window, withIntraday(repo, signals, now), now);
    }

    private GithubSignals withIntraday(RepoRef repo, GithubSignals signals, Instant now) {
        IntradaySource tracker = intradaySource.getIfAvailable();
        if (tracker == null || signals.nodeId() == null) {
            return signals;
        }
        if (signals.activity().to().isBefore(now.minus(INTRADAY_RELEVANCE))) {
            // Внутридневная история трекера не покрывает старые окна — не подмешиваем её.
            return signals;
        }
        try {
            tracker.watch(signals.nodeId(), repo, signals.repoCreatedAt(), signals.totalStars(), signals.totalForks());
            return signals.withIntraday(tracker.intraday(signals.nodeId()).orElse(null));
        } catch (RuntimeException e) {
            log.warn("Star tracker is unavailable, report for {} has no intraday data: {}",
                    repo.fullName(), e.getMessage());
            return signals;
        }
    }

    private GithubSignals fetch(
            RepoRef repo,
            Instant queryFrom,
            Instant queryTo,
            LocalDate endDay,
            LocalDate today,
            Integer days,
            Instant now,
            ArchiveProgress progress) {
        Map<String, Object> variables = new HashMap<>();
        variables.put("owner", repo.owner());
        variables.put("name", repo.name());
        variables.put("since", queryFrom.toString());
        variables.put("until", queryTo.toString());
        Map<String, Object> data = query(repo, REPO_QUERY, variables);
        Map<String, Object> repository = Graphql.map(data, "repository");
        progress.hourDone(1, 3);

        Instant createdAt = Graphql.instant(repository, "createdAt");
        if (createdAt == null) {
            throw new GithubUnavailableException("GITHUB_UNAVAILABLE", "GitHub returned no repository creation date");
        }
        LocalDate createdDay = createdAt.atZone(ZoneOffset.UTC).toLocalDate();
        if (createdDay.isAfter(endDay)) {
            throw new InvalidWindowException("repository was created after the selected period");
        }
        // Запрошенное окно сохраняем как есть (месяц — это месяц), даже если репозиторий
        // моложе; дневная история при этом начинается с даты создания.
        LocalDate windowFromDay = days == null ? createdDay : endDay.minusDays(days - 1L);
        Instant windowFrom = windowFromDay.atStartOfDay(ZoneOffset.UTC).toInstant();
        TimeWindow window = new TimeWindow(
                windowFrom, queryTo, (int) Duration.between(windowFrom, queryTo).toHours());

        // Для проверок грузим ещё и базовую линию до начала окна, но не раньше создания репозитория.
        LocalDate historyStart = today.minusDays(properties.historyDays() - 1L);
        LocalDate baselineStart = windowFromDay.minusDays(BASELINE_DAYS);
        LocalDate earliest = historyStart.isBefore(baselineStart) ? historyStart : baselineStart;
        LocalDate firstDay = createdDay.isAfter(earliest) ? createdDay : earliest;
        int[] daily = dailyStars(repo, firstDay, endDay, today);
        progress.hourDone(2, 3);

        int windowStars = 0;
        for (int i = 0; i < daily.length; i++) {
            if (!firstDay.plusDays(i).isBefore(windowFromDay)) {
                windowStars += daily[i];
            }
        }

        int[] forks = countInWindow(repo, repository, "forks", createdAt, windowFrom, queryTo, now);
        int[] issues = countInWindow(repo, repository, "issues", createdAt, windowFrom, queryTo, now);
        int[] pulls = countInWindow(repo, repository, "pullRequests", createdAt, windowFrom, queryTo, now);
        Map<String, Object> history = Graphql.map(repository, "defaultBranchRef", "target", "history");
        Set<String> authors = new HashSet<>();
        for (Object node : Graphql.list(history, "nodes")) {
            String login = Graphql.string(Graphql.map(node, "author", "user"), "login");
            if (login != null) {
                authors.add(login.toLowerCase(Locale.ROOT));
            }
        }
        GithubSignals.WindowActivity activity = new GithubSignals.WindowActivity(
                window.from(),
                window.to(),
                windowStars,
                forks[0],
                forks[1] == 1,
                issues[0],
                pulls[0],
                (int) Graphql.number(history, "totalCount", 0),
                authors.size());
        return new GithubSignals(
                Graphql.string(repository, "id"),
                createdAt,
                Graphql.number(repository, "stargazerCount", 0),
                Graphql.number(repository, "forkCount", 0),
                firstDay,
                daily,
                activity,
                null);
    }

    /**
     * Дневная история звёзд от {@code firstDay} до {@code endDay}. Страницы отдаются
     * от новых недель к старым, поэтому их число считаем от сегодняшнего дня.
     */
    private int[] dailyStars(RepoRef repo, LocalDate firstDay, LocalDate endDay, LocalDate today) {
        Map<LocalDate, Integer> byDay = new HashMap<>();
        long pagesSpan = Duration.between(firstDay.atStartOfDay(), today.atStartOfDay()).toDays();
        int pages = Math.min(MAX_HISTORY_PAGES, (int) Math.ceil((pagesSpan + 7.0) / DAYS_PER_PAGE));
        for (int page = 1; page <= Math.max(1, pages); page++) {
            ResponseEntity<List<StarHistoryWeek>> response = history(repo, page);
            List<StarHistoryWeek> weeks = response.getBody() == null ? List.of() : response.getBody();
            LocalDate oldest = null;
            for (StarHistoryWeek week : weeks) {
                LocalDate start = Instant.ofEpochSecond(week.week()).atZone(ZoneOffset.UTC).toLocalDate();
                List<Integer> days = week.days() == null ? List.of() : week.days();
                for (int i = 0; i < days.size(); i++) {
                    Integer stars = days.get(i);
                    if (stars != null && stars > 0) {
                        byDay.merge(start.plusDays(i), stars, Integer::sum);
                    }
                }
                oldest = oldest == null || start.isBefore(oldest) ? start : oldest;
            }
            if (!hasNext(response) || oldest == null || oldest.isBefore(firstDay)) {
                break;
            }
        }
        int span = (int) Duration.between(firstDay.atStartOfDay(), endDay.atStartOfDay()).toDays();
        int[] daily = new int[Math.max(1, span + 1)];
        for (int i = 0; i < daily.length; i++) {
            daily[i] = byDay.getOrDefault(firstDay.plusDays(i), 0);
        }
        return daily;
    }

    private int[] countInWindow(
            RepoRef repo,
            Map<String, Object> repository,
            String connection,
            Instant createdAt,
            Instant from,
            Instant to,
            Instant now) {
        Map<String, Object> first = Graphql.map(repository, connection);
        if (!createdAt.isBefore(from) && !to.isBefore(now)) {
            // Репозиторий создан внутри окна, которое заканчивается сейчас: все элементы попадают в окно.
            return new int[]{(int) Graphql.number(first, "totalCount", 0), 0};
        }
        int count = 0;
        Map<String, Object> page = first;
        for (int loaded = 1; ; loaded++) {
            for (Object node : Graphql.list(page, "nodes")) {
                Instant at = Graphql.instant(asMap(node), "createdAt");
                if (at == null) {
                    continue;
                }
                if (!at.isBefore(to)) {
                    continue;   // событие позже конца исторического окна
                }
                if (at.isBefore(from)) {
                    return new int[]{count, 0};
                }
                count++;
            }
            Map<String, Object> pageInfo = Graphql.map(page, "pageInfo");
            if (!Graphql.bool(pageInfo, "hasNextPage")) {
                return new int[]{count, 0};
            }
            if (loaded >= properties.maxForkPages()) {
                return new int[]{count, 1};
            }
            Map<String, Object> variables = new HashMap<>();
            variables.put("owner", repo.owner());
            variables.put("name", repo.name());
            variables.put("after", Graphql.string(pageInfo, "endCursor"));
            page = Graphql.map(query(repo, PAGE_QUERY.formatted(connection), variables), "repository", connection);
        }
    }

    private RepoData toRepoData(RepoRef repo, TimeWindow window, GithubSignals signals, Instant now) {
        GithubSignals.WindowActivity activity = signals.activity();
        EventCounts counts = new EventCounts(
                activity.stars() + activity.forks() + activity.commits() + activity.issues() + activity.pullRequests(),
                activity.stars(),
                activity.forks(),
                activity.commits(),
                activity.issues(),
                activity.pullRequests(),
                0);
        GithubSignals.Intraday intraday = signals.intraday();
        RepoStats stats = new RepoStats(repo, window, counts, activity.contributors(), 0,
                intraday == null ? 0 : peak10m(intraday), List.of());
        RepoSnapshot snapshot = new RepoSnapshot(repo, window, now, counts, activity.contributors(), 0, null,
                new DataAvailability(false, false, intraday != null && intraday.globalHourShare().length == 24),
                List.of(), StarIndex.empty(), signals);
        ArchiveFetchResult archive = new ArchiveFetchResult(
                List.of(), window.hours(), window.hours(), List.of(), 0, false, StarIndex.empty());
        DataSource source = new DataSource(DataSource.Type.GITHUB_API,
                activity.stars(), (int) Math.min(Integer.MAX_VALUE, signals.totalStars()),
                signals.firstDay().atStartOfDay(ZoneOffset.UTC).toInstant());
        return new RepoData(window, new RepoAggregate(stats, snapshot), archive, null, List.of(), source);
    }

    static int peak10m(GithubSignals.Intraday intraday) {
        int[] stars = intraday.stars();
        int window = Math.max(1, 10 / intraday.stepMinutes());
        int best = 0;
        for (int end = window; end < stars.length; end++) {
            int start = end - window;
            if (stars[end] >= 0 && stars[start] >= 0) {
                best = Math.max(best, stars[end] - stars[start]);
            }
        }
        return best;
    }

    private ResponseEntity<List<StarHistoryWeek>> history(RepoRef repo, int page) {
        ResponseEntity<List<StarHistoryWeek>> response;
        try {
            response = retryOnce(() -> rest.starHistory(repo.owner(), repo.name(), page));
        } catch (FeignException e) {
            throw failure(repo, e);
        }
        if (response.getStatusCode().value() == 202) {
            throw new GithubUnavailableException("GITHUB_HISTORY_NOT_READY",
                    "GitHub is still computing the star history of " + repo.fullName() + ", retry later");
        }
        return response;
    }

    private Map<String, Object> query(RepoRef repo, String query, Map<String, Object> variables) {
        Map<String, Object> response;
        try {
            response = retryOnce(() -> graphql.query(Map.of("query", query, "variables", variables)));
        } catch (FeignException e) {
            throw failure(repo, e);
        } catch (RuntimeException e) {
            throw new GithubUnavailableException("GITHUB_UNAVAILABLE", "GitHub API request failed", e);
        }
        Map<String, Object> data = Graphql.map(response, "data");
        if (Graphql.map(data, "repository") == null) {
            if (Graphql.notFound(response) || data != null) {
                throw new RepoGoneException(repo);
            }
            throw new GithubUnavailableException("GITHUB_UNAVAILABLE", "GitHub API returned no data");
        }
        return data;
    }

    private static <T> T retryOnce(java.util.function.Supplier<T> call) {
        try {
            return call.get();
        } catch (FeignException e) {
            if (e.status() < 502 || e.status() > 504) {
                throw e;
            }
            try {
                Thread.sleep(RETRY_DELAY_MILLIS);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw e;
            }
            return call.get();
        }
    }

    private static RuntimeException failure(RepoRef repo, FeignException e) {
        return switch (e.status()) {
            case NOT_FOUND -> new RepoGoneException(repo);
            case UNAUTHORIZED -> new GithubUnavailableException("GITHUB_TOKEN_REQUIRED", "GitHub rejected the token", e);
            case FORBIDDEN, TOO_MANY_REQUESTS -> new GithubUnavailableException("GITHUB_RATE_LIMITED",
                    "GitHub API rate limit reached or access denied: HTTP " + e.status(), e);
            default -> new GithubUnavailableException("GITHUB_UNAVAILABLE",
                    "GitHub API request failed: HTTP " + e.status(), e);
        };
    }

    private static boolean hasNext(ResponseEntity<?> response) {
        List<String> links = response.getHeaders().get("Link");
        return links != null && links.stream().anyMatch(link -> link.contains("rel=\"next\""));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object raw) {
        return raw instanceof Map<?, ?> ? (Map<String, Object>) raw : Map.of();
    }
}
