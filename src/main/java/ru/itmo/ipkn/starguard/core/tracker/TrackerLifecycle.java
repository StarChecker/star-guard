package ru.itmo.ipkn.starguard.core.tracker;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.support.CronTrigger;
import ru.itmo.ipkn.starguard.core.config.TrackerProperties;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.TimeZone;

public class TrackerLifecycle {

    private static final Logger log = LoggerFactory.getLogger(TrackerLifecycle.class);

    private final StarTracker tracker;
    private final TaskScheduler scheduler;
    private final TrackerProperties properties;

    public TrackerLifecycle(StarTracker tracker, TaskScheduler scheduler, TrackerProperties properties) {
        this.tracker = tracker;
        this.scheduler = scheduler;
        this.properties = properties;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        if (!tracker.active()) {
            log.warn("Star tracker is enabled but GITHUB_TOKEN is not set: polling is off");
            return;
        }
        scheduler.schedule(this::bootstrap, Instant.now());
        scheduler.scheduleAtFixedRate(this::poll, Instant.now().plus(properties.pollInterval()),
                properties.pollInterval());
        scheduler.schedule(this::refreshPopular,
                new CronTrigger(properties.popularRefreshCron(), TimeZone.getTimeZone(ZoneOffset.UTC)));
        scheduler.schedule(this::discoverRising,
                new CronTrigger(properties.rising().discoveryCron(), TimeZone.getTimeZone(ZoneOffset.UTC)));
        scheduler.scheduleWithFixedDelay(this::maintain, Instant.now().plus(Duration.ofMinutes(10)),
                Duration.ofHours(1));
    }

    private void bootstrap() {
        if (tracker.status().popular() == 0) {
            refreshPopular();
        }
        discoverRising();
        poll();
    }

    private void poll() {
        try {
            tracker.pollOnce();
        } catch (RuntimeException e) {
            log.error("Star tracker poll failed", e);
        }
    }

    private void refreshPopular() {
        try {
            tracker.refreshPopular();
        } catch (RuntimeException e) {
            log.error("Star tracker popular refresh failed", e);
        }
    }

    private void discoverRising() {
        try {
            tracker.discoverRising();
        } catch (RuntimeException e) {
            log.error("Star tracker rising discovery failed", e);
        }
    }

    private void maintain() {
        try {
            tracker.maintain();
        } catch (RuntimeException e) {
            log.error("Star tracker maintenance failed", e);
        }
    }
}
