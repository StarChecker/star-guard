package ru.itmo.ipkn.starguard.core.archive;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Component;
import ru.itmo.ipkn.starguard.core.config.ArchiveProperties;
import ru.itmo.ipkn.starguard.core.config.StarIndexProperties;
import ru.itmo.ipkn.starguard.core.domain.EventType;
import ru.itmo.ipkn.starguard.core.domain.GhEvent;
import ru.itmo.ipkn.starguard.core.domain.RepoRef;
import ru.itmo.ipkn.starguard.core.domain.StarIndex;
import ru.itmo.ipkn.starguard.core.domain.TimeWindow;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Офлайн-режим для тестов и демо: читает события из classpath-фикстур.
 * События сдвигаются во времени так, чтобы последнее попало в запрошенное окно.
 * Индекс звёзд для C4 строится по всем репозиториям фикстур, попавшим в окно.
 */
@Component
@ConditionalOnProperty(name = "app.archive.mode", havingValue = "fixture")
public class FixtureGhArchiveClient implements GhArchiveClient {

    private static final Logger log = LoggerFactory.getLogger(FixtureGhArchiveClient.class);

    private final List<GhEvent> fixtureEvents;
    private final int parseErrors;
    private final StarIndexProperties starIndexProperties;

    public FixtureGhArchiveClient(
            ArchiveProperties properties,
            GhArchiveParser parser,
            StarIndexProperties starIndexProperties) {
        this.starIndexProperties = starIndexProperties;
        List<GhEvent> events = new ArrayList<>();
        int errors = 0;
        ResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        try {
            for (Resource resource : resolver.getResources(properties.fixtureLocation())) {
                if (!resource.exists() || !resource.isReadable()) {
                    continue;
                }
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (line.isBlank()) {
                            continue;
                        }
                        try {
                            Optional<GhEvent> parsed = parser.parse(line);
                            parsed.ifPresent(events::add);
                        } catch (MalformedEventException e) {
                            errors++;
                        }
                    }
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Unable to read GH Archive fixtures from " + properties.fixtureLocation(), e);
        }
        this.fixtureEvents = List.copyOf(events);
        this.parseErrors = errors;
        log.info("Loaded {} fixture events ({} malformed) from {}",
                fixtureEvents.size(), parseErrors, properties.fixtureLocation());
    }

    @Override
    public ArchiveFetchResult fetchEvents(RepoRef repo, TimeWindow window) {
        if (fixtureEvents.isEmpty()) {
            return new ArchiveFetchResult(List.of(), window.hours(), 0, List.of(), parseErrors, true);
        }

        Instant latest = fixtureEvents.stream()
                .map(GhEvent::createdAt)
                .max(Comparator.naturalOrder())
                .orElseThrow();
        Duration shift = Duration.between(latest, window.to().minusSeconds(1));

        List<GhEvent> inWindow = fixtureEvents.stream()
                .map(event -> event.shiftedBy(shift))
                .filter(event -> !event.createdAt().isBefore(window.from()))
                .filter(event -> event.createdAt().isBefore(window.to()))
                .toList();

        List<GhEvent> events = inWindow.stream()
                .filter(event -> event.repoFullName().equalsIgnoreCase(repo.fullName()))
                .sorted(Comparator.comparing(GhEvent::createdAt).thenComparing(GhEvent::eventId))
                .toList();

        return new ArchiveFetchResult(
                events, window.hours(), window.hours(), List.of(), parseErrors, true,
                starIndex(inWindow, window.hours()));
    }

    private StarIndex starIndex(List<GhEvent> inWindow, int hours) {
        if (!starIndexProperties.enabled()) {
            return StarIndex.empty();
        }
        StarIndex.Builder builder = new StarIndex.Builder(starIndexProperties.maxPairsPerHour() * hours);
        for (GhEvent event : inWindow) {
            if (event.type() == EventType.WATCH) {
                builder.add(event.repoFullName(), event.actorId());
            }
        }
        return builder.build(hours);
    }
}
