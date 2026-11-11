package ru.itmo.ipkn.starguard.core.scoring;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import ru.itmo.ipkn.starguard.core.config.BurstCheckConfiguration;
import ru.itmo.ipkn.starguard.core.config.BurstProperties;
import ru.itmo.ipkn.starguard.core.domain.DataAvailability;
import ru.itmo.ipkn.starguard.core.domain.EventCounts;
import ru.itmo.ipkn.starguard.core.domain.RepoRef;
import ru.itmo.ipkn.starguard.core.domain.RepoSnapshot;
import ru.itmo.ipkn.starguard.core.domain.TimeWindow;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

class BurstCheckTest {
    private static final Instant FROM = Instant.parse("2026-11-09T00:00:00Z");
    private static final TimeWindow WINDOW = new TimeWindow(FROM, FROM.plusSeconds(86400), 24);
    private static final JsonMapper MAPPER = JsonMapper.builder().build();
    private final BurstCheck check = new BurstCheck(properties(10, 60));

    @Test
    void burstSpikeScoresHigh() throws IOException {
        CheckResult result = check.evaluate(fixture("burst_spike"));

        assertThat(result.checkId()).isEqualTo(CheckIds.BURST);
        assertThat(result.score()).isGreaterThanOrEqualTo(0.9);
        assertThat(result.confidence()).isEqualTo(1.0);
        assertThat(result.details())
                .containsEntry("mocked", false)
                .containsEntry("max_burst_10m", 40L)
                .containsEntry("ratio", 40.0)
                .containsEntry("window_start", FROM.plusSeconds(693 * 60).toString());
    }

    @Test
    void steadyGrowthScoresLow() throws IOException {
        CheckResult result = check.evaluate(fixture("steady_growth"));

        assertThat(result.score()).isLessThanOrEqualTo(0.2);
        assertThat(result.details()).containsEntry("max_burst_10m", 5L);
    }

    @Test
    void coldStartHasLowConfidence() throws IOException {
        CheckResult result = check.evaluate(fixture("cold_start"));

        assertThat(result.confidence()).isLessThan(0.5);
        assertThat(result.details())
                .containsEntry("reason", "insufficient_history")
                .containsEntry("observed_minutes", 20);
    }

    @Test
    void everyMinuteBeforeWarmupHasLowConfidence() {
        CheckResult before = check.evaluate(snapshot(new int[59], 0));
        CheckResult atWarmup = check.evaluate(snapshot(new int[60], 0));

        assertThat(before.confidence()).isLessThan(0.5);
        assertThat(atWarmup.confidence()).isEqualTo(1.0);
    }

    @Test
    void usesConfiguredSignificanceGate() throws IOException {
        BurstCheck configured = new BurstCheck(properties(80, 60));
        CheckResult result = configured.evaluate(fixture("burst_spike"));

        assertThat(result.score()).isCloseTo(0.5, within(1e-9));
    }

    @Test
    void usesMedianAsBaseline() {
        int[] minutes = new int[1440];
        Arrays.fill(minutes, 1);
        CheckResult result = check.evaluate(snapshot(minutes, 1440));

        assertThat(result.score()).isZero();
        assertThat(result.details())
                .containsEntry("baseline_10m", 10.0)
                .containsEntry("ratio", 1.0);
    }

    @Test
    void includesLastCompleteWindow() {
        int[] minutes = new int[1440];
        minutes[1430] = 6;
        minutes[1439] = 7;
        CheckResult result = check.evaluate(snapshot(minutes, 13));

        assertThat(result.details())
                .containsEntry("max_burst_10m", 13L)
                .containsEntry("window_start", FROM.plusSeconds(1430 * 60).toString());
    }

    @Test
    void missingMinuteSeriesHasNoConfidence() {
        CheckResult result = check.evaluate(snapshot(null, 0));

        assertThat(result.score()).isZero();
        assertThat(result.confidence()).isZero();
        assertThat(result.details()).containsEntry("reason", "missing_stars_by_minute");
    }

    @Test
    void disabledCheckIsNotRegistered() {
        new ApplicationContextRunner()
                .withUserConfiguration(BurstCheckConfiguration.class)
                .withPropertyValues(BurstProperties.ENABLED_PROPERTY + "=false",
                        BurstProperties.MODE_PROPERTY + "=real")
                .run(context -> assertThat(context.getBeansOfType(BurstCheck.class)).isEmpty());
    }

    @Test
    void rejectsIncompleteAndInvalidProperties() {
        assertThatThrownBy(() -> new BurstProperties(CheckMode.REAL, true, null, 12.0, 10, 60, 0.30))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new BurstProperties(CheckMode.REAL, true, 3.0, 0.0, 10, 60, 0.30))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static BurstProperties properties(int minBurst, int warmupMinutes) {
        return new BurstProperties(CheckMode.REAL, true, 3.0, 12.0, minBurst, warmupMinutes, 0.30);
    }

    private static RepoSnapshot fixture(String name) throws IOException {
        try (InputStream stream = BurstCheckTest.class.getResourceAsStream("/fixtures/burst/" + name + ".json")) {
            assertThat(stream).isNotNull();
            JsonNode root = MAPPER.readTree(new String(stream.readAllBytes(), StandardCharsets.UTF_8));
            int[] minutes = new int[root.path("minutes_observed").asInt()];
            JsonNode pattern = root.path("pattern");
            for (int minute = 0; minute < minutes.length; minute++) {
                minutes[minute] = pattern.get(minute % pattern.size()).asInt();
            }
            JsonNode points = root.path("points");
            for (int i = 0; i < points.size(); i++) {
                JsonNode point = points.get(i);
                minutes[point.path("minute").asInt()] += point.path("stars").asInt();
            }
            int stars = root.path("stars_24h").asInt();
            assertThat(Arrays.stream(minutes).sum()).isEqualTo(stars);
            return snapshot(minutes, stars);
        }
    }

    private static RepoSnapshot snapshot(int[] minutes, int stars) {
        EventCounts counts = new EventCounts(stars, stars, 0, 0, 0, 0, 0);
        return new RepoSnapshot(new RepoRef("demo", "repo"), WINDOW, WINDOW.to(), counts,
                stars, stars, minutes, DataAvailability.pocDefaults());
    }
}
