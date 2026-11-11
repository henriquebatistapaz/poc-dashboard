package com.henrique.collector.feed;

import static org.junit.jupiter.api.Assertions.*;

import com.henrique.collector.source.CollectorSource;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class RssFeedParserTest {
    private final RssFeedParser parser = new RssFeedParser();

    @Test
    void parsesRssItemAndKeepsOnlyMinimalMetadata() throws IOException {
        List<FeedItem> result = parser.parse(resource("/feeds/google-news.xml"), CollectorSource.GOOGLE_NEWS);
        assertEquals(1, result.size());
        FeedItem item = result.getFirst();
        assertEquals("news-42", item.sourceId());
        assertEquals("Example headline", item.title());
        assertEquals("https://example.org/story", item.link().toString());
        assertEquals(Instant.parse("2026-11-05T12:00:00Z"), item.publishedAt());
        assertEquals(CollectorSource.GOOGLE_NEWS, item.source());
        assertNotNull(item.retrievedAt());
    }

    @Test
    void parsesAtomEntryAndAlternateLink() throws IOException {
        List<FeedItem> result = parser.parse(resource("/feeds/wordpress-atom.xml"), CollectorSource.WORDPRESS);
        assertEquals(1, result.size());
        assertEquals("tag:example.org,2026:8", result.getFirst().sourceId());
        assertEquals("https://example.org/posts/8", result.getFirst().link().toString());
    }

    @Test
    void fallsBackToCanonicalLinkWhenNoStableIdExists() {
        byte[] xml = "<rss><channel><item><title>Fallback</title><link>https://example.org/x</link></item></channel></rss>"
                .getBytes(StandardCharsets.UTF_8);
        assertEquals("https://example.org/x", parser.parse(xml, CollectorSource.WORDPRESS).getFirst().sourceId());
    }

    @Test
    void rejectsNonFeedXml() {
        assertThrows(FeedParseException.class,
                () -> parser.parse("<html/>".getBytes(StandardCharsets.UTF_8), CollectorSource.WORDPRESS));
    }

    @Test
    void rejectsTruncatedFeedXml() {
        byte[] xml = "<rss><channel><item>".getBytes(StandardCharsets.UTF_8);
        assertThrows(FeedParseException.class, () -> parser.parse(xml, CollectorSource.WORDPRESS));
    }

    @Test
    void rejectsExternalEntityPayload() {
        String xml = "<!DOCTYPE rss [<!ENTITY x SYSTEM 'file:///etc/passwd'>]><rss><channel><item><title>&x;</title>"
                + "<link>https://example.org</link></item></channel></rss>";
        assertThrows(FeedParseException.class,
                () -> parser.parse(xml.getBytes(StandardCharsets.UTF_8), CollectorSource.WORDPRESS));
    }

    @Test
    void rejectsNonHttpItemLinks() {
        byte[] xml = "<rss><channel><item><title>Bad</title><link>file:///tmp/a</link></item></channel></rss>"
                .getBytes(StandardCharsets.UTF_8);
        assertThrows(FeedParseException.class, () -> parser.parse(xml, CollectorSource.WORDPRESS));
    }

    private byte[] resource(String name) throws IOException {
        try (var input = getClass().getResourceAsStream(name)) {
            assertNotNull(input);
            return input.readAllBytes();
        }
    }
}
