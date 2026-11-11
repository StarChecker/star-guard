package ru.itmo.ipkn.starguard.core.archive;

import org.junit.jupiter.api.Test;
import ru.itmo.ipkn.starguard.core.domain.EventType;
import ru.itmo.ipkn.starguard.core.domain.GhEvent;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GhArchiveParserTest {

    private final GhArchiveParser parser = new GhArchiveParser();

    @Test
    void parsesWatchEvent() {
        String line = """
                {"id":"1","type":"WatchEvent","repo":{"id":42,"name":"demo/star-guard"},
                 "actor":{"id":7,"login":"dev"},"created_at":"2026-11-09T10:15:30Z"}
                """;

        Optional<GhEvent> parsed = parser.parse(line);

        assertThat(parsed).isPresent();
        GhEvent event = parsed.orElseThrow();
        assertThat(event.eventId()).isEqualTo("1");
        assertThat(event.type()).isEqualTo(EventType.WATCH);
        assertThat(event.repoId()).isEqualTo(42L);
        assertThat(event.repoFullName()).isEqualTo("demo/star-guard");
        assertThat(event.actorId()).isEqualTo(7L);
        assertThat(event.actorLogin()).isEqualTo("dev");
        assertThat(event.createdAt()).isEqualTo(Instant.parse("2026-11-09T10:15:30Z"));
        assertThat(event.actorCreatedAt()).isNull();
    }

    @Test
    void parsesActorCreatedAtWhenPresent() {
        String line = """
                {"id":"4","type":"WatchEvent","repo":{"id":42,"name":"demo/star-guard"},
                 "actor":{"id":7,"login":"dev","created_at":"2026-11-01T08:00:00Z"},
                 "created_at":"2026-11-09T10:15:30Z"}
                """;

        assertThat(parser.parse(line).orElseThrow().actorCreatedAt())
                .isEqualTo(Instant.parse("2026-11-01T08:00:00Z"));
    }

    @Test
    void keepsEventWithInvalidActorCreatedAt() {
        String line = """
                {"id":"5","type":"WatchEvent","repo":{"id":42,"name":"demo/star-guard"},
                 "actor":{"id":7,"login":"dev","created_at":"yesterday"},
                 "created_at":"2026-11-09T10:15:30Z"}
                """;

        GhEvent event = parser.parse(line).orElseThrow();
        assertThat(event.createdAt()).isEqualTo(Instant.parse("2026-11-09T10:15:30Z"));
        assertThat(event.actorCreatedAt()).isNull();
    }

    @Test
    void mapsUnknownTypeToOther() {
        String line = """
                {"id":"2","type":"ReleaseEvent","repo":{"id":42,"name":"demo/star-guard"},
                 "actor":{"id":7,"login":"dev"},"created_at":"2026-11-09T10:15:30Z"}
                """;

        assertThat(parser.parse(line).orElseThrow().type()).isEqualTo(EventType.OTHER);
    }

    @Test
    void skipsEventWithoutRepo() {
        String line = """
                {"id":"3","type":"WatchEvent","actor":{"id":7,"login":"dev"},
                 "created_at":"2026-11-09T10:15:30Z"}
                """;

        assertThat(parser.parse(line)).isEmpty();
    }

    @Test
    void throwsOnMalformedJson() {
        assertThatThrownBy(() -> parser.parse("{not-json"))
                .isInstanceOf(MalformedEventException.class);
    }

    @Test
    void parsesLineInsideLargerBuffer() {
        byte[] buffer = ("{\"broken\n" + WATCH + "\n{tail").getBytes(StandardCharsets.UTF_8);
        int offset = "{\"broken\n".length();
        int length = WATCH.getBytes(StandardCharsets.UTF_8).length;

        GhEvent event = parser.parse(buffer, offset, length, EventFilter.ALL).orElseThrow();

        assertThat(event).isEqualTo(parser.parse(WATCH).orElseThrow());
    }

    @Test
    void ignoresSameNamedFieldsNestedInPayload() {
        String line = """
                {"id":"6","type":"PullRequestEvent","actor":{"id":7,"login":"dev"},
                 "repo":{"id":42,"name":"demo/star-guard"},
                 "payload":{"id":1,"type":"Bot","repo":{"id":5,"name":"evil/decoy"},
                            "actor":{"id":9,"login":"decoy"},"created_at":"2001-01-01T00:00:00Z"},
                 "created_at":"2026-11-09T10:15:30Z"}
                """;

        GhEvent event = parser.parse(line).orElseThrow();

        assertThat(event.eventId()).isEqualTo("6");
        assertThat(event.type()).isEqualTo(EventType.PULL_REQUEST);
        assertThat(event.repoFullName()).isEqualTo("demo/star-guard");
        assertThat(event.actorLogin()).isEqualTo("dev");
        assertThat(event.createdAt()).isEqualTo(Instant.parse("2026-11-09T10:15:30Z"));
    }

    @Test
    void starsFilterStopsRightAfterType() {
        // После "type" строка битая: полный разбор падает, а лёгкий проход до поломки не доходит.
        String line = "{\"id\":\"7\",\"type\":\"PushEvent\",\"actor\":{\"id\":7,\"login\":\"dev\"},\"payload\":{oops";

        assertThat(parser.parse(line, EventFilter.stars())).isEmpty();
        assertThatThrownBy(() -> parser.parse(line))
                .isInstanceOf(MalformedEventException.class);
    }

    @Test
    void repoFilterStopsRightAfterRepo() {
        String line = "{\"id\":\"8\",\"type\":\"IssuesEvent\",\"actor\":{\"id\":7},"
                + "\"repo\":{\"id\":9,\"name\":\"other/repo\"},\"payload\":{oops";

        assertThat(parser.parse(line, EventFilter.repo("demo/star-guard"))).isEmpty();
        assertThatThrownBy(() -> parser.parse(line, EventFilter.repo("OTHER/Repo")))
                .isInstanceOf(MalformedEventException.class);
    }

    @Test
    void appliesFilterToCompleteEventWhenFieldsComeOutOfOrder() {
        // repo раньше type: решение по типу принимается, когда известны оба поля.
        String push = "{\"repo\":{\"id\":42,\"name\":\"demo/star-guard\"},\"type\":\"PushEvent\","
                + "\"id\":\"9\",\"actor\":{\"id\":7},\"created_at\":\"2026-11-09T10:15:30Z\"}";
        String watch = push.replace("PushEvent", "WatchEvent");

        assertThat(parser.parse(push, EventFilter.stars())).isEmpty();
        assertThat(parser.parse(watch, EventFilter.stars())).isPresent();
        assertThat(parser.parse(push, EventFilter.repo("DEMO/star-guard"))).isPresent();
    }

    @Test
    void combinedFilterKeepsRepositoryEventsAndStarsOfOthers() {
        EventFilter filter = EventFilter.repo("demo/star-guard").or(EventFilter.stars());

        assertThat(parser.parse(WATCH.replace("demo/star-guard", "other/repo"), filter)).isPresent();
        assertThat(parser.parse(WATCH.replace("WatchEvent", "IssuesEvent"), filter)).isPresent();
        assertThat(parser.parse(WATCH.replace("WatchEvent", "IssuesEvent")
                .replace("demo/star-guard", "other/repo"), filter)).isEmpty();
    }

    private static final String WATCH = "{\"id\":\"1\",\"type\":\"WatchEvent\","
            + "\"actor\":{\"id\":7,\"login\":\"dev\"},\"repo\":{\"id\":42,\"name\":\"demo/star-guard\"},"
            + "\"payload\":{\"action\":\"started\"},\"created_at\":\"2026-11-09T10:15:30Z\"}";
}
