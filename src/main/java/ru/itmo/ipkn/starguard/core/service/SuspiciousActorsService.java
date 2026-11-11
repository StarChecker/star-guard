package ru.itmo.ipkn.starguard.core.service;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import ru.itmo.ipkn.starguard.core.config.ApiProperties;
import ru.itmo.ipkn.starguard.core.domain.ActorActivity;
import ru.itmo.ipkn.starguard.core.domain.ActorProfile;
import ru.itmo.ipkn.starguard.core.domain.RepoSnapshot;
import ru.itmo.ipkn.starguard.core.domain.StarIndex;
import ru.itmo.ipkn.starguard.core.domain.StarMark;
import ru.itmo.ipkn.starguard.core.domain.SuspiciousActor;
import ru.itmo.ipkn.starguard.core.scoring.ActorOverlapCheck;
import ru.itmo.ipkn.starguard.core.scoring.CheckIds;
import ru.itmo.ipkn.starguard.core.scoring.FreshAccountsCheck;
import ru.itmo.ipkn.starguard.core.scoring.RepoScore;

import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Единый набор причин для списка аккаунтов и разметки звёзд; лимит не влияет на график. */
@Service
public class SuspiciousActorsService {
    private final ObjectProvider<ActorOverlapCheck> overlap;
    private final ObjectProvider<FreshAccountsCheck> fresh;
    private final ApiProperties properties;

    public SuspiciousActorsService(ObjectProvider<ActorOverlapCheck> overlap,
                                   ObjectProvider<FreshAccountsCheck> fresh, ApiProperties properties) {
        this.overlap = overlap;
        this.fresh = fresh;
        this.properties = properties;
    }

    public Map<Long, Set<String>> flags(RepoSnapshot snapshot, RepoScore score, List<StarMark> stars) {
        return flags(snapshot, score, stars, List.of());
    }

    public Map<Long, Set<String>> flags(RepoSnapshot snapshot, RepoScore score, List<StarMark> stars,
                                       List<ActorActivity> actors) {
        Map<Long, Set<String>> flags = new HashMap<>();
        ActorOverlapCheck c4 = overlap.getIfAvailable();
        if (c4 != null && isRealAndActive(score, CheckIds.ACTOR_OVERLAP)) {
            c4.flaggedActors(snapshot).forEach(id ->
                    flags.computeIfAbsent(id, ignored -> new HashSet<>()).add(CheckIds.ACTOR_OVERLAP));
        }
        FreshAccountsCheck c2 = fresh.getIfAvailable();
        if (c2 != null && isRealAndActive(score, CheckIds.FRESH_ACCOUNTS)) {
            Set<String> hashes = c2.freshActorHashes(snapshot);
            Set<Long> candidates = new HashSet<>();
            // Профили C2 доступны и при отключённом/обрезанном глобальном star-index.
            actors.stream().filter(ActorActivity::starred).forEach(actor -> candidates.add(actor.actorId()));
            stars.stream().filter(mark -> inWindow(snapshot, mark)).forEach(mark -> candidates.add(mark.actorId()));
            for (long id : candidates) {
                if (hashes.contains(ActorProfile.hashOf(id))) {
                    flags.computeIfAbsent(id, ignored -> new HashSet<>()).add(CheckIds.FRESH_ACCOUNTS);
                }
            }
        }
        return flags;
    }

    public List<SuspiciousActor> sample(RepoSnapshot snapshot, List<ActorActivity> actors,
                                       List<StarMark> stars, Map<Long, Set<String>> flags) {
        if (flags.isEmpty()) return List.of();
        Map<Long, ActorActivity> profiles = new HashMap<>();
        actors.forEach(actor -> profiles.merge(actor.actorId(), actor, ActorActivity::merge));
        Map<Long, Instant> firstStars = new HashMap<>();
        stars.stream().filter(mark -> inWindow(snapshot, mark)).forEach(mark ->
                firstStars.merge(mark.actorId(), mark.at(), (a, b) -> a.isBefore(b) ? a : b));
        Map<Long, Integer> shared = new HashMap<>();
        String target = StarIndex.normalize(snapshot.repo().fullName());
        snapshot.starIndex().forEachRepo((repo, ids) -> {
            if (!repo.equals(target)) {
                for (long id : ids) {
                    if (flags.containsKey(id)) shared.merge(id, 1, Integer::sum);
                }
            }
        });
        return flags.entrySet().stream().map(entry -> {
            long id = entry.getKey();
            ActorActivity actor = profiles.get(id);
            String login = actor != null && !actor.login().isBlank()
                    && properties.actorLogins() == ApiProperties.ActorLogins.SHOW ? actor.login() : null;
            List<String> reasons = entry.getValue().stream()
                    .map(check -> check.equals(CheckIds.FRESH_ACCOUNTS) ? "fresh" : "overlap").sorted().toList();
            return new SuspiciousActor(ActorProfile.hashOf(id), login, reasons,
                    actor == null ? null : actor.accountCreatedAt(),
                    actor == null ? firstStars.get(id) : actor.firstSeen(), shared.getOrDefault(id, 0));
        }).sorted(Comparator.<SuspiciousActor>comparingInt(actor -> actor.reasons().size()).reversed()
                .thenComparing(Comparator.comparingInt(SuspiciousActor::sharedRepos).reversed())
                .thenComparing(SuspiciousActor::actorHash))
                .limit(properties.suspiciousActorsLimit()).toList();
    }

    static boolean isRealAndActive(RepoScore score, String checkId) {
        return score.checks().stream().anyMatch(check -> check.checkId().equals(checkId)
                && check.active() && check.score() > 0 && check.error() == null
                && !Boolean.TRUE.equals(check.details().get("mocked")));
    }

    private static boolean inWindow(RepoSnapshot snapshot, StarMark mark) {
        return !mark.at().isBefore(snapshot.window().from()) && mark.at().isBefore(snapshot.window().to());
    }
}
