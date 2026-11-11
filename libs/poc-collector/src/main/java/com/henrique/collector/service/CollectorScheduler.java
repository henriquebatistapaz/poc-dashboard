package com.henrique.collector.service;

import com.henrique.collector.source.CollectorService;
import java.time.Instant;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public final class CollectorScheduler implements AutoCloseable {
    private final CollectorService collector;
    private final CollectorStateStore store;
    private final SecretBox secretBox;
    private final long retentionDays;
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();

    public CollectorScheduler(CollectorService collector, CollectorStateStore store,
                              SecretBox secretBox, long retentionDays) {
        this.collector = collector;
        this.store = store;
        this.secretBox = secretBox;
        this.retentionDays = retentionDays;
    }

    public void start() {
        executor.scheduleWithFixedDelay(this::tickSafely, 5, 15, TimeUnit.SECONDS);
    }

    private void tickSafely() {
        try {
            tick();
        } catch (RuntimeException ignored) {
        }
    }

    void tick() {
        Instant now = Instant.now();
        store.deleteExpired(now.minusSeconds(retentionDays * 86_400));
        for (ScheduledFeed feed : store.dueFeeds(now)) {
            String status = "SUCCEEDED";
            try {
                store.addItems(collector.collect(feed.source(), secretBox.decrypt(feed.encryptedInput())));
            } catch (RuntimeException e) {
                status = "FAILED";
            } finally {
                store.completeFeed(feed.id(), Instant.now().plusSeconds(feed.intervalSeconds()), status);
            }
        }
    }

    @Override
    public void close() {
        executor.shutdownNow();
    }
}
