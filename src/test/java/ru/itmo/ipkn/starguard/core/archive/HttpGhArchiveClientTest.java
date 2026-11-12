package ru.itmo.ipkn.starguard.core.archive;

import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import ru.itmo.ipkn.starguard.core.domain.GhEvent;
import ru.itmo.ipkn.starguard.core.domain.RepoRef;
import ru.itmo.ipkn.starguard.core.domain.TimeWindow;
import ru.itmo.ipkn.starguard.core.metrics.StarGuardMetrics;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.zip.GZIPOutputStream;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "app.index.enabled=false")
class HttpGhArchiveClientTest {

    private static final WireMockServer WIRE_MOCK = new WireMockServer(options().dynamicPort());

    static {
        WIRE_MOCK.start();
    }

    private static final TimeWindow WINDOW = new TimeWindow(
            Instant.parse("2026-11-09T00:00:00Z"),
            Instant.parse("2026-11-09T03:00:00Z"),
            3);

    @DynamicPropertySource
    static void archiveProperties(DynamicPropertyRegistry registry) {
        registry.add("app.archive.base-url", () -> "http://127.0.0.1:" + WIRE_MOCK.port());
    }

    @AfterAll
    static void stopWireMock() {
        WIRE_MOCK.stop();
    }

    @BeforeEach
    void resetWireMock() {
        WIRE_MOCK.resetAll();
    }

    @Autowired
    private GhArchiveClient client;

    @Autowired
    private MeterRegistry registry;

    @Test
    void streamsGzipFiltersRepositoryAndReportsMissingHours() throws IOException {
        WIRE_MOCK.stubFor(get(urlEqualTo("/2026-11-09-0.json.gz"))
                .willReturn(aResponse().withStatus(200).withBody(gzip("""
                        {"id":"1","type":"WatchEvent","repo":{"id":1,"name":"demo/star-guard"},"actor":{"id":10,"login":"alice"},"created_at":"2026-11-09T00:10:00Z"}
                        {"id":"2","type":"PushEvent","repo":{"id":2,"name":"other/repo"},"actor":{"id":11,"login":"bob"},"created_at":"2026-11-09T00:11:00Z"}
                        {"id":"3","type":"ForkEvent","repo":{"id":1,"name":"demo/star-guard"},"actor":{"id":12,"login":"carol"},"created_at":"2026-11-09T00:12:00Z"}
                        {broken-json
                        """))));
        WIRE_MOCK.stubFor(get(urlEqualTo("/2026-11-09-1.json.gz"))
                .willReturn(aResponse().withStatus(404)));
        WIRE_MOCK.stubFor(get(urlEqualTo("/2026-11-09-2.json.gz"))
                .willReturn(aResponse().withStatus(200).withBody(gzip("""
                        {"id":"4","type":"WatchEvent","repo":{"id":1,"name":"demo/star-guard"},"actor":{"id":13,"login":"dave"},"created_at":"2026-11-09T02:10:00Z"}
                        """))));

        ArchiveFetchResult result = client.fetchEvents(new RepoRef("demo", "star-guard"), WINDOW);

        assertThat(result.events()).extracting(GhEvent::eventId).containsExactly("1", "3", "4");
        assertThat(result.events()).allMatch(event -> event.repoFullName().equals("demo/star-guard"));
        assertThat(result.hoursRequested()).isEqualTo(3);
        assertThat(result.hoursLoaded()).isEqualTo(2);
        assertThat(result.missingHours()).containsExactly(Instant.parse("2026-11-09T01:00:00Z"));
        assertThat(result.parseErrors()).isEqualTo(1);
        assertThat(result.cached()).isFalse();
    }

    @Test
    void servesSecondRequestFromCache() throws IOException {
        RepoRef repo = new RepoRef("demo", "cached");
        String hour0 = """
                {"id":"1","type":"WatchEvent","repo":{"id":3,"name":"demo/cached"},"actor":{"id":10,"login":"alice"},"created_at":"2026-11-09T00:10:00Z"}
                """;
        String hour1 = """
                {"id":"2","type":"WatchEvent","repo":{"id":3,"name":"demo/cached"},"actor":{"id":11,"login":"bob"},"created_at":"2026-11-09T01:10:00Z"}
                """;
        String hour2 = """
                {"id":"3","type":"WatchEvent","repo":{"id":3,"name":"demo/cached"},"actor":{"id":12,"login":"carol"},"created_at":"2026-11-09T02:10:00Z"}
                """;
        WIRE_MOCK.stubFor(get(urlEqualTo("/2026-11-09-0.json.gz"))
                .willReturn(aResponse().withStatus(200).withBody(gzip(hour0))));
        WIRE_MOCK.stubFor(get(urlEqualTo("/2026-11-09-1.json.gz"))
                .willReturn(aResponse().withStatus(200).withBody(gzip(hour1))));
        WIRE_MOCK.stubFor(get(urlEqualTo("/2026-11-09-2.json.gz"))
                .willReturn(aResponse().withStatus(200).withBody(gzip(hour2))));

        ArchiveFetchResult first = client.fetchEvents(repo, WINDOW);
        ArchiveFetchResult second = client.fetchEvents(repo, WINDOW);

        assertThat(first.events()).hasSize(3);
        assertThat(first.cached()).isFalse();
        assertThat(second.events()).hasSize(3);
        assertThat(second.cached()).isTrue();
        WIRE_MOCK.verify(1, getRequestedFor(urlEqualTo("/2026-11-09-0.json.gz")));
    }

    @Test
    void collectsStarIndexOfAllRepositoriesInSamePass() throws IOException {
        TimeWindow window = new TimeWindow(
                Instant.parse("2026-11-08T00:00:00Z"), Instant.parse("2026-11-08T02:00:00Z"), 2);
        WIRE_MOCK.stubFor(get(urlEqualTo("/2026-11-08-0.json.gz"))
                .willReturn(aResponse().withStatus(200).withBody(gzip("""
                        {"id":"11","type":"WatchEvent","repo":{"id":1,"name":"demo/a"},"actor":{"id":1,"login":"u1"},"created_at":"2026-11-08T00:01:00Z"}
                        {"id":"12","type":"WatchEvent","repo":{"id":1,"name":"demo/a"},"actor":{"id":2,"login":"u2"},"created_at":"2026-11-08T00:02:00Z"}
                        {"id":"13","type":"WatchEvent","repo":{"id":2,"name":"Other/B"},"actor":{"id":1,"login":"u1"},"created_at":"2026-11-08T00:03:00Z"}
                        {"id":"14","type":"PushEvent","repo":{"id":3,"name":"other/c"},"actor":{"id":3,"login":"u3"},"created_at":"2026-11-08T00:04:00Z"}
                        """))));
        WIRE_MOCK.stubFor(get(urlEqualTo("/2026-11-08-1.json.gz"))
                .willReturn(aResponse().withStatus(200).withBody(gzip("""
                        {"id":"15","type":"WatchEvent","repo":{"id":2,"name":"other/b"},"actor":{"id":2,"login":"u2"},"created_at":"2026-11-08T01:01:00Z"}
                        {"id":"16","type":"WatchEvent","repo":{"id":2,"name":"other/b"},"actor":{"id":1,"login":"u1"},"created_at":"2026-11-08T01:02:00Z"}
                        {"id":"17","type":"ForkEvent","repo":{"id":1,"name":"demo/a"},"actor":{"id":4,"login":"u4"},"created_at":"2026-11-08T01:03:00Z"}
                        """))));

        ArchiveFetchResult first = client.fetchEvents(new RepoRef("demo", "a"), window);
        ArchiveFetchResult second = client.fetchEvents(new RepoRef("demo", "a"), window);

        assertThat(first.events()).extracting(GhEvent::eventId).containsExactly("11", "12", "17");
        for (ArchiveFetchResult result : java.util.List.of(first, second)) {
            assertThat(result.starIndex().available()).isTrue();
            assertThat(result.starIndex().hoursCovered()).isEqualTo(2);
            assertThat(result.starIndex().truncated()).isFalse();
            assertThat(result.starIndex().actors("demo/a")).containsExactly(1L, 2L);
            assertThat(result.starIndex().actors("other/b")).containsExactly(1L, 2L);
            assertThat(result.starIndex().actors("other/c")).isEmpty();
        }
        assertThat(second.cached()).isTrue();
    }

    @Test
    void recordsParseMetricsForScan() throws IOException {
        TimeWindow window = new TimeWindow(
                Instant.parse("2026-11-07T00:00:00Z"), Instant.parse("2026-11-07T02:00:00Z"), 2);
        WIRE_MOCK.stubFor(get(urlEqualTo("/2026-11-07-0.json.gz"))
                .willReturn(aResponse().withStatus(200).withBody(gzip("""
                        {"id":"21","type":"WatchEvent","repo":{"id":1,"name":"demo/metered"},"actor":{"id":1,"login":"u1"},"created_at":"2026-11-07T00:01:00Z"}
                        {"id":"22","type":"PushEvent","repo":{"id":2,"name":"other/repo"},"actor":{"id":2,"login":"u2"},"created_at":"2026-11-07T00:02:00Z"}
                        {broken-json
                        """))));
        WIRE_MOCK.stubFor(get(urlEqualTo("/2026-11-07-1.json.gz"))
                .willReturn(aResponse().withStatus(404)));
        double lines = count(StarGuardMetrics.PARSE_LINES, null);
        double errors = count(StarGuardMetrics.PARSE_ERRORS, null);
        double parsed = count(StarGuardMetrics.PARSE_HOURS, "parsed");
        double missing = count(StarGuardMetrics.PARSE_HOURS, "missing");

        client.fetchEvents(new RepoRef("demo", "metered"), window);

        assertThat(count(StarGuardMetrics.PARSE_LINES, null) - lines).isEqualTo(3);
        assertThat(count(StarGuardMetrics.PARSE_ERRORS, null) - errors).isEqualTo(1);
        assertThat(count(StarGuardMetrics.PARSE_HOURS, "parsed") - parsed).isEqualTo(1);
        assertThat(count(StarGuardMetrics.PARSE_HOURS, "missing") - missing).isEqualTo(1);
        assertThat(registry.get(StarGuardMetrics.PARSE_LINES_PER_SECOND).tag("source", "scan").gauge().value())
                .isPositive();
    }

    private double count(String name, String outcome) {
        var search = registry.find(name).tag("source", "scan");
        if (outcome != null) {
            search = search.tag("outcome", outcome);
        }
        Counter counter = search.counter();
        return counter == null ? 0 : counter.count();
    }

    private static byte[] gzip(String content) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(bytes)) {
            gzip.write(content.getBytes(StandardCharsets.UTF_8));
        }
        return bytes.toByteArray();
    }
}
