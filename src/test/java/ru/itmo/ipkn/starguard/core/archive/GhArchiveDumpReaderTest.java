package ru.itmo.ipkn.starguard.core.archive;

import org.junit.jupiter.api.Test;
import ru.itmo.ipkn.starguard.core.domain.GhEvent;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

class GhArchiveDumpReaderTest {

    private final GhArchiveParser parser = new GhArchiveParser();

    @Test
    void readsGzipDumpWithFilterAndCountsBrokenLines() throws IOException {
        String dump = String.join("\n",
                line("1", "WatchEvent", "demo/star-guard"),
                line("2", "IssuesEvent", "demo/star-guard"),
                "",
                line("3", "WatchEvent", "other/repo") + "\r",
                line("4", "PullRequestEvent", "other/repo"),
                "{not-json",
                "   ",
                line("5", "ForkEvent", "Demo/Star-Guard"));

        List<GhEvent> all = new ArrayList<>();
        int allErrors = GhArchiveDumpReader.read(gzip(dump), parser, EventFilter.ALL, all::add);

        List<GhEvent> filtered = new ArrayList<>();
        int filteredErrors = GhArchiveDumpReader.read(gzip(dump), parser,
                EventFilter.repo("demo/star-guard").or(EventFilter.stars()), filtered::add);

        assertThat(all).extracting(GhEvent::eventId).containsExactly("1", "2", "3", "4", "5");
        assertThat(allErrors).isEqualTo(1);
        assertThat(filtered).extracting(GhEvent::eventId).containsExactly("1", "2", "3", "5");
        assertThat(filteredErrors).isEqualTo(1);
    }

    @Test
    void countsLinesBytesAndErrorsForMetrics() throws IOException {
        String first = line("1", "WatchEvent", "demo/star-guard");
        String second = line("2", "PushEvent", "other/repo");
        String dump = String.join("\n", first, "", second, "{not-json", "   ");

        List<GhEvent> events = new ArrayList<>();
        ParseStats stats = GhArchiveDumpReader.readCounted(gzip(dump), parser,
                EventFilter.repo("demo/star-guard"), events::add);

        assertThat(events).extracting(GhEvent::eventId).containsExactly("1");
        assertThat(stats.lines()).isEqualTo(3);
        assertThat(stats.bytes()).isEqualTo(first.length() + second.length() + "{not-json".length());
        assertThat(stats.errors()).isEqualTo(1);
    }

    private static String line(String id, String type, String repo) {
        return "{\"id\":\"" + id + "\",\"type\":\"" + type + "\",\"actor\":{\"id\":7,\"login\":\"dev\"},"
                + "\"repo\":{\"id\":42,\"name\":\"" + repo + "\"},\"payload\":{\"action\":\"x\"},"
                + "\"created_at\":\"2026-11-09T10:15:30Z\"}";
    }

    private static ByteArrayInputStream gzip(String content) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(bytes)) {
            gzip.write(content.getBytes(StandardCharsets.UTF_8));
        }
        return new ByteArrayInputStream(bytes.toByteArray());
    }
}
