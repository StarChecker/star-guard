package ru.itmo.ipkn.starguard.core.index;

import ru.itmo.ipkn.starguard.core.domain.ActorActivity;

import java.util.Arrays;
import java.util.Comparator;
import java.util.Map;

/** Снимок индекса в сравнимом виде: агрегаты с минутными рядами, акторы, индекс звёзд. */
final class IndexSnapshots {

    private IndexSnapshots() {
    }

    static String describe(RepoIndexSnapshot snapshot) {
        StringBuilder out = new StringBuilder()
                .append(snapshot.window()).append(" loaded=").append(snapshot.hoursLoaded())
                .append(" missing=").append(snapshot.missingHours())
                .append(" errors=").append(snapshot.parseErrors()).append('\n');
        snapshot.repos().entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            out.append(entry.getKey()).append(" -> ").append(entry.getValue().repo().fullName()).append('\n');
            for (RepoHourAggregate hour : entry.getValue().hours()) {
                out.append("  ").append(hour.hour()).append(' ').append(hour.counts())
                        .append(' ').append(Arrays.toString(hour.starsByMinute()))
                        .append(" actors=").append(hour.uniqueActors()).append('/').append(hour.starActors())
                        .append(' ').append(hour.actors().stream()
                                .sorted(Comparator.comparingLong(ActorActivity::actorId)).toList())
                        .append('\n');
            }
        });
        snapshot.starIndexByHour().entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry ->
                out.append("stars ").append(entry.getKey()).append(' ')
                        .append(entry.getValue().repoCount()).append('/')
                        .append(entry.getValue().pairCount()).append('\n'));
        return out.toString();
    }
}
