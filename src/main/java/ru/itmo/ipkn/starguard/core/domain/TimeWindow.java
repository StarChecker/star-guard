package ru.itmo.ipkn.starguard.core.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Окно анализа: последние {@code hours} полных часов UTC, заканчивая на now - lag.
 * Интервал полуоткрытый: [from, to).
 */
public record TimeWindow(Instant from, Instant to, int hours) {

    public TimeWindow {
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        if (!from.isBefore(to)) {
            throw new IllegalArgumentException("window.from must be before window.to");
        }
        if (hours < 1) {
            throw new IllegalArgumentException("window.hours must be positive");
        }
    }

    /** Начала часовых файлов GH Archive, покрывающих окно, по возрастанию. */
    public List<Instant> hourStarts() {
        List<Instant> starts = new ArrayList<>(hours);
        Instant current = from;
        for (int i = 0; i < hours; i++) {
            starts.add(current);
            current = current.plus(Duration.ofHours(1));
        }
        return List.copyOf(starts);
    }
}
