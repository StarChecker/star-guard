package ru.itmo.ipkn.starguard.core.archive;

import org.springframework.stereotype.Component;
import ru.itmo.ipkn.starguard.core.config.ArchiveProperties;
import ru.itmo.ipkn.starguard.core.domain.TimeWindow;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Вычисляет окно последних N полных часов UTC с учётом задержки публикации GH Archive.
 */
@Component
public class TimeWindowCalculator {

    private final Clock clock;
    private final ArchiveProperties properties;

    public TimeWindowCalculator(Clock clock, ArchiveProperties properties) {
        this.clock = clock;
        this.properties = properties;
    }

    public TimeWindow window(Integer requestedHours) {
        int hours = requestedHours == null ? properties.defaultWindowHours() : requestedHours;
        if (hours < 1 || hours > properties.maxWindowHours()) {
            throw new InvalidWindowException("hours must be within [1, %d]"
                    .formatted(properties.maxWindowHours()));
        }
        Instant to = properties.windowEnd() != null
                ? properties.windowEnd()
                : clock.instant()
                        .minus(Duration.ofHours(properties.lagHours()))
                        .truncatedTo(ChronoUnit.HOURS);
        return new TimeWindow(to.minus(Duration.ofHours(hours)), to, hours);
    }
}
