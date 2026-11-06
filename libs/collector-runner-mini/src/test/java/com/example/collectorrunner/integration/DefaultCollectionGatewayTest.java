package com.example.collectorrunner.integration;

import com.example.rsscollector.application.RssFeedLinkValidator;
import com.henrique.collector.feed.RssFeedParser;
import com.henrique.collector.source.*;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DefaultCollectionGatewayTest {
    static final String RSS = """
        <rss version="2.0"><channel><title>demo</title><item><guid>1</guid>
        <title>Integration title</title><link>https://example.org/1</link></item></channel></rss>
        """;
    static final String ATOM = """
        <feed xmlns="http://www.w3.org/2005/Atom"><id>feed</id><title>demo</title>
        <updated>2026-11-06T00:00:00Z</updated><entry><id>1</id><title>Atom title</title>
        <link href="https://example.org/atom"/><updated>2026-11-06T00:00:00Z</updated></entry></feed>
        """;

    private DefaultCollectionGateway gateway() {
        com.henrique.collector.feed.FeedFetcher fetcher = uri -> RSS.getBytes(StandardCharsets.UTF_8);
        var parser = new RssFeedParser();
        var reddit = mock(RedditCollector.class);
        doThrow(new SourceAccessException("disabled")).when(reddit).collect(anyString());
        var collectors = new CollectorService(new SourceRegistry(), new GoogleNewsCollector(fetcher, parser),
                new GoogleAlertsCollector(fetcher, parser, new GoogleAlertsUrlPolicy()),
                new WordPressFeedCollector(fetcher, parser, new PublicHttpsFeedUrlPolicy(host ->
                        new InetAddress[]{InetAddress.getByAddress(new byte[]{8,8,8,8})})), reddit);
        var urls = new RssFeedLinkValidator();
        return new DefaultCollectionGateway(collectors, url -> ATOM,
                new com.example.rsscollector.application.RssFeedParser(urls), urls);
    }

    @Test
    void mapsEachImplementedFeedSource() {
        var gateway = gateway();
        assertEquals("google-news", gateway.collect(CollectionSource.GOOGLE_NEWS, "java").getFirst().source());
        assertEquals("wordpress", gateway.collect(CollectionSource.WORDPRESS, "https://example.org/feed").getFirst().source());
        assertEquals("google-alerts", gateway.collect(CollectionSource.GOOGLE_ALERTS, "https://www.google.com/alerts/feeds/demo/secret").getFirst().source());
        var rss = gateway.collect(CollectionSource.RSS, "https://example.org/feed").getFirst();
        assertEquals("rss", rss.source());
        assertEquals("Atom title", rss.title());
        assertNotEquals(rss.externalId(), gateway.collect(CollectionSource.RSS, "https://other.example.org/feed").getFirst().externalId());
    }

    @Test
    void reusesSocialAdaptersForSyntheticPosts() {
        var alpha = gateway().collect(CollectionSource.ALPHA, """
            {"posts":[{"id":"1","author":{"handle":"demo"},"text":"Alpha title",
            "created_at":"2026-11-06T00:00:00Z","url":"https://example.org/alpha"}]}
            """);
        var beta = gateway().collect(CollectionSource.BETA, """
            {"items":[{"postId":"1","authorName":"demo","body":"Beta title",
            "publishedAt":"2026-11-06T00:00:00Z","link":"https://example.org/beta"}]}
            """);
        assertEquals("alpha", alpha.getFirst().source());
        assertEquals("Alpha title", alpha.getFirst().title());
        assertEquals("beta", beta.getFirst().source());
        assertEquals("Beta title", beta.getFirst().title());
    }

    @Test
    void preservesSourceAvailabilityAndUrlRules() {
        var gateway = gateway();
        assertThrows(SourceAccessException.class, () -> gateway.collect(CollectionSource.ISSUU, "anything"));
        assertThrows(SourceAccessException.class, () -> DefaultCollectionGateway.fromEnvironment(Map.of()).collect(CollectionSource.REDDIT, "java"));
        assertThrows(IllegalArgumentException.class, () -> gateway.collect(CollectionSource.RSS, "http://127.0.0.1/feed"));
        assertThrows(IllegalArgumentException.class, () -> gateway.collect(CollectionSource.GOOGLE_ALERTS, "https://example.org/feed"));
    }
}
