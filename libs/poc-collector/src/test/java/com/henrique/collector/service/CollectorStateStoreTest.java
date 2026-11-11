package com.henrique.collector.service;

import static org.junit.jupiter.api.Assertions.*;

import com.henrique.collector.feed.FeedItem;
import com.henrique.collector.source.CollectorSource;
import java.net.URI;
import java.nio.file.Path;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CollectorStateStoreTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void persistsDeduplicatedItemsAndEncryptedScheduledFeeds() {
        Path file = temporaryDirectory.resolve("collector.json");
        Instant retrievedAt = Instant.parse("2026-11-11T12:00:00Z");
        var item = new FeedItem(CollectorSource.WORDPRESS, "item-1", "Title",
                URI.create("https://example.test/item"), null, retrievedAt);
        var store = new CollectorStateStore(file);
        store.addItems(java.util.List.of(item, item));
        ScheduledFeed feed = store.createFeed(CollectorSource.WORDPRESS, "ciphertext", 3_600,
                retrievedAt.plusSeconds(3_600));

        var reopened = new CollectorStateStore(file);
        assertEquals(1, reopened.items(null, 10).size());
        assertEquals("ciphertext", reopened.feeds().getFirst().encryptedInput());
        assertEquals(feed.id(), reopened.feeds().getFirst().id());
        assertTrue(reopened.deleteFeed(feed.id()));
        assertTrue(reopened.feeds().isEmpty());
    }

    @Test
    void removesItemsPastConfiguredRetentionCutoff() {
        Path file = temporaryDirectory.resolve("collector.json");
        var store = new CollectorStateStore(file);
        Instant now = Instant.parse("2026-11-11T12:00:00Z");
        store.addItems(java.util.List.of(new FeedItem(CollectorSource.WORDPRESS, "old", "Title",
                URI.create("https://example.test/item"), null, now.minusSeconds(86_401))));

        assertEquals(1, store.deleteExpired(now.minusSeconds(86_400)));
        assertTrue(store.items(null, 10).isEmpty());
    }
}
