package ru.itmo.ipkn.starguard.core.archive;

import org.springframework.stereotype.Component;
import ru.itmo.ipkn.starguard.core.domain.EventType;
import ru.itmo.ipkn.starguard.core.domain.GhEvent;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.core.ObjectReadContext;
import tools.jackson.core.exc.InputCoercionException;
import tools.jackson.core.json.JsonFactory;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.Optional;

/**
 * Потоковый парсер NDJSON-события GH Archive.
 * <p>
 * Читает строку токенами ({@link JsonParser}), а не деревом: из события берутся только
 * {@code id}, {@code type}, {@code created_at}, {@code repo.id/name} и
 * {@code actor.id/login/created_at}, всё остальное (прежде всего {@code payload},
 * ~70% байтов строки) пропускается без построения объектов и декодирования строк.
 * {@link EventFilter} позволяет остановиться ещё раньше: после {@code type}
 * (проход 1 индекса — только звёзды) или после {@code repo.name}
 * (проход 2 и запрос одного репозитория).
 * <p>
 * Правила валидации совпадают с прежним разбором через {@code readTree}: без
 * {@code id}/{@code type}/{@code created_at}/{@code repo.name}, с нулевым
 * {@code repo.id} или {@code actor.id} событие пропускается; синтаксически битая
 * строка — {@link MalformedEventException}.
 */
@Component
public class GhArchiveParser {

    private final JsonFactory factory = JsonFactory.builder().build();

    /** Полный разбор строки (фикстуры, тесты). */
    public Optional<GhEvent> parse(String line) {
        return parse(line, EventFilter.ALL);
    }

    /** Разбор строки с ранним фильтром. */
    public Optional<GhEvent> parse(String line, EventFilter filter) {
        try (JsonParser parser = factory.createParser(ObjectReadContext.empty(), line)) {
            return Optional.ofNullable(read(parser, filter));
        } catch (JacksonException e) {
            throw new MalformedEventException("Malformed event JSON", e);
        }
    }

    /**
     * Разбор строки дампа прямо из байтов UTF-8, без декодирования в {@code String}.
     * Строку с битым UTF-8 разбирает повторно через декодирование с заменой (U+FFFD) —
     * так же, как прежний путь через {@code InputStreamReader}, чтобы такие строки
     * не превращались в {@code parse_errors}.
     */
    public Optional<GhEvent> parse(byte[] buffer, int offset, int length, EventFilter filter) {
        try (JsonParser parser = factory.createParser(ObjectReadContext.empty(), buffer, offset, length)) {
            return Optional.ofNullable(read(parser, filter));
        } catch (JacksonException e) {
            return parse(new String(buffer, offset, length, StandardCharsets.UTF_8), filter);
        }
    }

    public static String normalizeFullName(String fullName) {
        return fullName == null ? "" : fullName.toLowerCase(Locale.ROOT);
    }

    private static GhEvent read(JsonParser parser, EventFilter filter) {
        if (parser.nextToken() != JsonToken.START_OBJECT) {
            return null;
        }
        Fields fields = new Fields();
        String name;
        while ((name = parser.nextName()) != null) {
            JsonToken token = parser.nextToken();
            switch (name) {
                case "id" -> fields.eventId = text(parser, token);
                case "type" -> {
                    fields.type = text(parser, token);
                    fields.eventType = fields.type == null ? null : mapType(fields.type);
                    if (fields.eventType != null && !filter.mayAccept(fields.eventType, fields.repoName)) {
                        return null;
                    }
                }
                case "created_at" -> fields.createdAt = text(parser, token);
                case "repo" -> {
                    readRepo(parser, token, fields);
                    if (fields.repoName != null && !filter.mayAccept(fields.eventType, fields.repoName)) {
                        return null;
                    }
                }
                case "actor" -> readActor(parser, token, fields);
                default -> parser.skipChildren();
            }
        }

        if (fields.eventId == null || fields.type == null || fields.createdAt == null
                || fields.repoName == null || fields.repoId == 0L || fields.actorId == 0L) {
            return null;
        }
        Instant createdAt = instant(fields.createdAt);
        if (createdAt == null) {
            return null;
        }
        GhEvent event = new GhEvent(
                fields.eventId,
                fields.eventType,
                fields.repoId,
                fields.repoName,
                fields.actorId,
                fields.actorLogin,
                createdAt,
                instant(fields.actorCreatedAt));
        return filter.accepts(event) ? event : null;
    }

    /** Повторный ключ {@code repo} заменяет предыдущий целиком — как в дереве Jackson. */
    private static void readRepo(JsonParser parser, JsonToken token, Fields fields) {
        fields.repoId = 0L;
        fields.repoName = null;
        if (token != JsonToken.START_OBJECT) {
            parser.skipChildren();
            return;
        }
        String name;
        while ((name = parser.nextName()) != null) {
            JsonToken value = parser.nextToken();
            switch (name) {
                case "id" -> fields.repoId = longValue(parser, value);
                case "name" -> fields.repoName = text(parser, value);
                default -> parser.skipChildren();
            }
        }
    }

    private static void readActor(JsonParser parser, JsonToken token, Fields fields) {
        fields.actorId = 0L;
        fields.actorLogin = null;
        fields.actorCreatedAt = null;
        if (token != JsonToken.START_OBJECT) {
            parser.skipChildren();
            return;
        }
        String name;
        while ((name = parser.nextName()) != null) {
            JsonToken value = parser.nextToken();
            switch (name) {
                case "id" -> fields.actorId = longValue(parser, value);
                case "login" -> fields.actorLogin = text(parser, value);
                case "created_at" -> fields.actorCreatedAt = text(parser, value);
                default -> parser.skipChildren();
            }
        }
    }

    /** Текст скаляра как {@code JsonNode.asText()}: объект/массив/null/пустая строка — {@code null}. */
    private static String text(JsonParser parser, JsonToken token) {
        if (token == JsonToken.START_OBJECT || token == JsonToken.START_ARRAY) {
            parser.skipChildren();
            return null;
        }
        if (token == null || token == JsonToken.VALUE_NULL) {
            return null;
        }
        String text = parser.getString();
        return text == null || text.isBlank() ? null : text;
    }

    /** Число как {@code JsonNode.asLong(0)}: строка "42" — 42, нечисловое — 0. */
    private static long longValue(JsonParser parser, JsonToken token) {
        if (token == JsonToken.START_OBJECT || token == JsonToken.START_ARRAY) {
            parser.skipChildren();
            return 0L;
        }
        try {
            return parser.getValueAsLong(0L);
        } catch (InputCoercionException e) {
            return 0L;
        }
    }

    private static Instant instant(String text) {
        if (text == null) {
            return null;
        }
        try {
            return Instant.parse(text);
        } catch (DateTimeParseException e) {
            return null;
        }
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

    /** Поля одной строки, нужные для {@link GhEvent}. */
    private static final class Fields {
        private String eventId;
        private String type;
        private EventType eventType;
        private String createdAt;
        private long repoId;
        private String repoName;
        private long actorId;
        private String actorLogin;
        private String actorCreatedAt;
    }
}
