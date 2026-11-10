package ru.itmo.ipkn.starguard.core.archive;

import org.springframework.stereotype.Component;
import ru.itmo.ipkn.starguard.core.domain.EventType;
import ru.itmo.ipkn.starguard.core.domain.GhEvent;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.Optional;

/** Построчный парсер NDJSON-события GH Archive. */
@Component
public class GhArchiveParser {

    private final JsonMapper mapper = JsonMapper.builder().build();

    public Optional<GhEvent> parse(String line) {
        JsonNode node;
        try {
            node = mapper.readTree(line);
        } catch (RuntimeException e) {
            throw new MalformedEventException("Malformed event JSON", e);
        }
        if (node == null || !node.isObject()) {
            return Optional.empty();
        }

        JsonNode repo = node.get("repo");
        JsonNode actor = node.get("actor");
        String eventId = text(node, "id");
        String type = text(node, "type");
        String createdAtText = text(node, "created_at");
        if (repo == null || actor == null || eventId == null || type == null || createdAtText == null) {
            return Optional.empty();
        }

        String repoName = text(repo, "name");
        long repoId = repo.path("id").asLong(0L);
        long actorId = actor.path("id").asLong(0L);
        if (repoName == null || repoId == 0L || actorId == 0L) {
            return Optional.empty();
        }

        Instant createdAt;
        try {
            createdAt = Instant.parse(createdAtText);
        } catch (DateTimeParseException e) {
            return Optional.empty();
        }

        return Optional.of(new GhEvent(
                eventId,
                mapType(type),
                repoId,
                repoName,
                actorId,
                text(actor, "login"),
                createdAt));
    }

    private static EventType mapType(String type) {
        return switch (type) {
            case "WatchEvent" -> EventType.WATCH;
            case "ForkEvent" -> EventType.FORK;
            case "PushEvent" -> EventType.PUSH;
            case "IssuesEvent" -> EventType.ISSUES;
            case "PullRequestEvent" -> EventType.PULL_REQUEST;
            default -> EventType.OTHER;
        };
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        String text = value.asText();
        return text == null || text.isBlank() ? null : text;
    }

    public static String normalizeFullName(String fullName) {
        return fullName == null ? "" : fullName.toLowerCase(Locale.ROOT);
    }
}
