package dev.henrique.collector.source;

import static org.junit.jupiter.api.Assertions.*;

import dev.henrique.collector.feed.FeedItem;
import dev.henrique.collector.feed.RssFeedParser;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class GoogleNewsCollectorTest {
    @Test
    void encodesSearchQueryAndMapsResults() throws IOException {
        byte[] fixture;
        try (var input = getClass().getResourceAsStream("/feeds/google-news.xml")) { fixture = input.readAllBytes(); }
        AtomicReference<URI> requested = new AtomicReference<>();
        GoogleNewsCollector collector = new GoogleNewsCollector(uri -> { requested.set(uri); return fixture; }, new RssFeedParser());

        var items = collector.collect("open data & maps");

        assertTrue(requested.get().toString().startsWith("https://news.google.com/rss/search?q=open+data+%26+maps"));
        assertEquals("en-US", parameter(requested.get(), "hl"));
        assertEquals("US", parameter(requested.get(), "gl"));
        assertEquals(1, items.size());
        assertEquals(CollectorSource.GOOGLE_NEWS, items.getFirst().source());
    }

    @Test
    void rejectsEmptyOrOversizedQuery() {
        var collector = new GoogleNewsCollector(uri -> new byte[0], new RssFeedParser());
        assertThrows(IllegalArgumentException.class, () -> collector.feedUri("  "));
        assertThrows(IllegalArgumentException.class, () -> collector.collect("x".repeat(201)));
    }

    @Test
    void rejectsInvalidLocaleAtConstruction() {
        assertThrows(IllegalArgumentException.class,
                () -> new GoogleNewsCollector(uri -> new byte[0], new RssFeedParser(), "en-US&x=1", "US", "US:en"));
    }

    private String parameter(URI uri, String key) {
        return java.util.Arrays.stream(uri.getRawQuery().split("&"))
                .filter(pair -> pair.startsWith(key + "="))
                .map(pair -> pair.substring(pair.indexOf('=') + 1))
                .findFirst().orElseThrow();
    }
}
