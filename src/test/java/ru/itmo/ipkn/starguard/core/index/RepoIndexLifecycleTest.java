package ru.itmo.ipkn.starguard.core.index;

import org.junit.jupiter.api.Test;
import ru.itmo.ipkn.starguard.core.config.ArchiveProperties;
import ru.itmo.ipkn.starguard.core.config.RepoIndexProperties;
import ru.itmo.ipkn.starguard.core.domain.TimeWindow;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class RepoIndexLifecycleTest {

    private static final Instant END = Instant.parse("2024-11-10T15:00:00Z");
    private static final RepoIndexProperties INDEX = new RepoIndexProperties(true, 10, 6, false, "0 10 * * * *");
    private static final RepoIndexSnapshot SNAPSHOT = new RepoIndexSnapshot(
            new TimeWindow(END.minus(Duration.ofHours(6)), END, 6), END, 6, List.of(), 0, Map.of());

    @Test
    void fixedWindowIsBuiltOnceAndNotRefreshed() throws InterruptedException {
        CountingBuilder builder = new CountingBuilder();
        RepoIndex index = new RepoIndex();
        RepoIndexLifecycle lifecycle = new RepoIndexLifecycle(builder, index, INDEX, archive(END));

        lifecycle.refreshAsync();
        awaitSnapshot(index);
        lifecycle.refreshAsync();
        Thread.sleep(100);

        assertThat(builder.builds.get()).isEqualTo(1);
    }

    @Test
    void slidingWindowIsRefreshedOnEveryTrigger() throws InterruptedException {
        CountingBuilder builder = new CountingBuilder();
        RepoIndex index = new RepoIndex();
        index.swap(SNAPSHOT);
        RepoIndexLifecycle lifecycle = new RepoIndexLifecycle(builder, index, INDEX, archive(null));

        lifecycle.refreshAsync();
        long deadline = System.currentTimeMillis() + 2000;
        while (builder.builds.get() == 0 && System.currentTimeMillis() < deadline) {
            Thread.sleep(10);
        }

        assertThat(builder.builds.get()).isEqualTo(1);
    }

    private static void awaitSnapshot(RepoIndex index) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 2000;
        while (index.snapshot().isEmpty() && System.currentTimeMillis() < deadline) {
            Thread.sleep(10);
        }
        assertThat(index.snapshot()).isPresent();
    }

    private static ArchiveProperties archive(Instant windowEnd) {
        return new ArchiveProperties("https://example.test", ArchiveProperties.Mode.HTTP,
                6, 24, 2, 4, 0, false, Duration.ofHours(1), null, windowEnd);
    }

    private static final class CountingBuilder extends RepoIndexBuilder {

        private final AtomicInteger builds = new AtomicInteger();

        private CountingBuilder() {
            super(null, null, null, null, null);
        }

        @Override
        public RepoIndexSnapshot build() {
            builds.incrementAndGet();
            return SNAPSHOT;
        }
    }
}
