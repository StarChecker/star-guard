package ru.itmo.ipkn.starguard.core.tracker;

import ru.itmo.ipkn.starguard.core.github.GithubTrackerClient;

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
        List<TrackerStore.NewRepo> ranked = new ArrayList<>(size);
        Set<String> seen = new HashSet<>();
        Integer upper = null;
        Integer remaining = null;
        boolean complete = true;
        while (ranked.size() < size) {
            String query = (upper == null ? "stars:>=1" : "stars:1.." + upper) + " sort:stars";
            String after = null;
            int added = 0;
            int lowest = Integer.MAX_VALUE;
            for (int page = 0; page < SEARCH_PAGES && ranked.size() < size; page++) {
                GithubTrackerClient.SearchPage result = client.search(query, after);
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
            if (upper < 1) {
                break;
            }
        }
        return new Result(ranked, remaining, complete && ranked.size() >= size);
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
