package com.henrique.collector;

import static org.junit.jupiter.api.Assertions.*;

import com.henrique.collector.feed.FeedItem;
import com.henrique.collector.source.CollectorSource;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class FeedItemJsonTest {
    @Test
    void escapesTextAndIncludesOnlyFeedMetadata() {
        FeedItem item = new FeedItem(CollectorSource.WORDPRESS, "id-1", "A \"title\"\nnext",
                URI.create("https://example.org/a"), null, Instant.parse("2026-11-05T00:00:00Z"));
        String json = FeedItemJson.encode(List.of(item));
        assertTrue(json.contains("A \\\"title\\\"\\nnext"));
        assertTrue(json.contains("\"publishedAt\":null"));
        assertFalse(json.contains("description"));
    }

    @Test
    void encodesEmptyCollectionAsJsonArray() {
        assertEquals("[]", FeedItemJson.encode(List.of()));
    }
}
