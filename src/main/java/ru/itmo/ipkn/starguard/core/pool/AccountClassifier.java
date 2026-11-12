package ru.itmo.ipkn.starguard.core.pool;

import ru.itmo.ipkn.starguard.core.config.AccountPoolProperties;
import ru.itmo.ipkn.starguard.core.github.GithubAccountPoolClient.Profile;
import java.util.HashMap;

public final class AccountClassifier {
    private AccountClassifier() {}
    public static double score(Profile profile, AccountPoolProperties p) {
        if (profile.totalStars() < p.minStars() || profile.repos() > p.maxRepos()
                || profile.followers() > p.maxFollowers() || profile.contributions() > p.maxContributions()
                || profile.stars().size() < p.burstSize() * 2) return 0;
        var minutes = new HashMap<Long, Integer>();
        profile.stars().forEach(s -> minutes.merge(s.at().getEpochSecond() / 60, 1, Integer::sum));
        var bursts = minutes.values().stream().filter(n -> n >= p.burstSize()).toList();
        double share = bursts.stream().mapToInt(Integer::intValue).sum() / (double) profile.stars().size();
        if (bursts.size() < 2 || share < p.burstShare()) return 0;
        return share * Math.min(1.0, profile.totalStars() / (double) (p.minStars() * 5L));
    }
}
