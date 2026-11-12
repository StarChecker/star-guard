package ru.itmo.ipkn.starguard.core.tracker;

import ru.itmo.ipkn.starguard.core.github.GithubTrackerClient;
import ru.itmo.ipkn.starguard.core.github.GithubUnavailableException;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class PopularWalker {

    private static final int SEARCH_PAGES = 10;

    private final GithubTrackerClient client;
    private final Duration pageDelay;
    private final int rateLimitReserve;

    public PopularWalker(GithubTrackerClient client, Duration pageDelay, int rateLimitReserve) {
        this.client = client;
        this.pageDelay = pageDelay;
        this.rateLimitReserve = rateLimitReserve;
    }

    public Result walk(int size) {
        return walk(size, "", 1);
    }

    public Result walk(int size, String filter, int minStars) {
        String prefix = filter.isBlank() ? "" : filter.trim() + " ";
        List<TrackerStore.NewRepo> ranked = new ArrayList<>(size);
        Set<String> seen = new HashSet<>();
        Integer upper = null;
        Integer remaining = null;
        boolean complete = true;
        while (ranked.size() < size) {
            String query = prefix + (upper == null ? "stars:>=" + minStars : "stars:" + minStars + ".." + upper)
                    + " sort:stars";
            String after = null;
            int added = 0;
            int lowest = Integer.MAX_VALUE;
            for (int page = 0; page < SEARCH_PAGES && ranked.size() < size; page++) {
                GithubTrackerClient.SearchPage result = search(query, after);
                remaining = result.rateLimit().remaining();
                for (GithubTrackerClient.RepoHit hit : result.repos()) {
                    lowest = Math.min(lowest, hit.stars());
                    if (ranked.size() < size && seen.add(hit.nodeId())) {
                        ranked.add(new TrackerStore.NewRepo(hit.nodeId(), hit.fullName(), hit.createdAt(),
                                hit.stars(), hit.forks()));
                        added++;
                    }
                }
                if (remaining < rateLimitReserve) {
                    complete = false;
                    break;
                }
                if (!result.hasNext()) {
                    break;
                }
                after = result.endCursor();
                pause();
            }
            if (!complete || lowest == Integer.MAX_VALUE) {
                break;
            }
            upper = added == 0 ? lowest - 1 : lowest;
            if (upper < minStars) {
                break;
            }
        }
        return new Result(ranked, remaining, complete && ranked.size() >= size);
    }

    // Search pages fail transiently (502, timeouts, empty data); one failed page must not drop the whole walk.
    private GithubTrackerClient.SearchPage search(String query, String after) {
        try {
            return client.search(query, after);
        } catch (GithubUnavailableException e) {
            if (!"GITHUB_UNAVAILABLE".equals(e.code())) {
                throw e;
            }
            pause();
            return client.search(query, after);
        }
    }

    private void pause() {
        if (pageDelay.isZero()) {
            return;
        }
        try {
            Thread.sleep(pageDelay.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public record Result(List<TrackerStore.NewRepo> ranked, Integer rateRemaining, boolean complete) {
    }
}
