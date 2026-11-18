package com.henrique.collector.source;

import static org.junit.jupiter.api.Assertions.*;

import com.henrique.collector.feed.RssFeedParser;
import java.net.URI;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class WordPressFeedCollectorTest {
    @Test
    void buildsNormalizedTagFeedUriAndParsesAtom() {
        byte[] xml;
        try (var input = getClass().getResourceAsStream("/feeds/wordpress-atom.xml")) {
            xml = input.readAllBytes();
        } catch (Exception e) { throw new AssertionError(e); }
        AtomicReference<URI> requested = new AtomicReference<>();
        var collector = new WordPressFeedCollector(uri -> {
            requested.set(uri);
            return xml;
        }, new RssFeedParser());

        var items = collector.collect("  Inteligência Artificial & Java  ");

        assertEquals(URI.create("https://wordpress.com/tag/inteligencia-artificial-java/feed/"), requested.get());
        assertEquals(1, items.size());
        assertEquals(CollectorSource.WORDPRESS, items.getFirst().source());
    }

    @Test
    void rejectsBlankOversizedOrPunctuationOnlyQueries() {
        var collector = new WordPressFeedCollector(uri -> new byte[0], new RssFeedParser());
        assertThrows(IllegalArgumentException.class, () -> collector.collect(null));
        assertThrows(IllegalArgumentException.class, () -> collector.collect("   "));
        assertThrows(IllegalArgumentException.class, () -> collector.collect("!!!"));
        assertThrows(IllegalArgumentException.class, () -> collector.collect("a".repeat(201)));
    }
}
