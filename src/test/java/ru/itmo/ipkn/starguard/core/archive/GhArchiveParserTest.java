package ru.itmo.ipkn.starguard.core.archive;

import org.junit.jupiter.api.Test;
import ru.itmo.ipkn.starguard.core.domain.EventType;
import ru.itmo.ipkn.starguard.core.domain.GhEvent;

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
}
