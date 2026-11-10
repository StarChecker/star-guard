package ru.itmo.ipkn.starguard.core.archive;

import org.junit.jupiter.api.Test;
import ru.itmo.ipkn.starguard.core.config.ArchiveProperties;
import ru.itmo.ipkn.starguard.core.domain.TimeWindow;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TimeWindowCalculatorTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-10T12:37:00Z"), ZoneOffset.UTC);
    private static final ArchiveProperties PROPERTIES = new ArchiveProperties(
            "https://example.test", ArchiveProperties.Mode.HTTP,
            6, 24, 2, 4, 1, false, Duration.ofHours(1), null);

    private final TimeWindowCalculator calculator = new TimeWindowCalculator(CLOCK, PROPERTIES);

    @Test
    void alignsWindowToCompleteHoursWithLag() {
        TimeWindow window = calculator.window(6);

        assertThat(window.to()).isEqualTo(Instant.parse("2026-11-10T10:00:00Z"));
        assertThat(window.from()).isEqualTo(Instant.parse("2026-11-10T04:00:00Z"));
        assertThat(window.hours()).isEqualTo(6);
        assertThat(window.hourStarts()).hasSize(6);
        assertThat(window.hourStarts().getFirst()).isEqualTo(window.from());
    }

    @Test
    void usesDefaultHoursWhenNotProvided() {
        assertThat(calculator.window(null).hours()).isEqualTo(6);
    }

    @Test
    void rejectsHoursOutsideConfiguredRange() {
        assertThatThrownBy(() -> calculator.window(0)).isInstanceOf(InvalidWindowException.class);
        assertThatThrownBy(() -> calculator.window(25)).isInstanceOf(InvalidWindowException.class);
    }
}
