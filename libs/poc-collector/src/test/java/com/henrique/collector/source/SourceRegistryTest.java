package com.henrique.collector.source;

import static org.junit.jupiter.api.Assertions.*;

import com.henrique.collector.feed.RssFeedParser;
import org.junit.jupiter.api.Test;

class SourceRegistryTest {
    @Test
    void describesCapabilityOfEveryRequestedSource() {
        SourceRegistry registry = new SourceRegistry();
        assertEquals(5, registry.all().size());
        assertEquals(CapabilityStatus.CONDITIONAL, registry.get(CollectorSource.GOOGLE_NEWS).status());
        assertEquals(CapabilityStatus.CONDITIONAL, registry.get(CollectorSource.GOOGLE_ALERTS).status());
        assertEquals(CapabilityStatus.AVAILABLE, registry.get(CollectorSource.WORDPRESS).status());
        assertEquals(CapabilityStatus.CONDITIONAL, registry.get(CollectorSource.REDDIT).status());
        assertEquals(CapabilityStatus.UNAVAILABLE, registry.get(CollectorSource.ISSUU).status());
    }

    @Test
    void refusesCollectorsWithoutPermittedAccess() {
        SourceRegistry registry = new SourceRegistry();
        var fetcher = (com.henrique.collector.feed.FeedFetcher) uri -> new byte[0];
        var parser = new RssFeedParser();
        var news = new GoogleNewsCollector(fetcher, parser);
        var alerts = new GoogleAlertsCollector(fetcher, parser, new GoogleAlertsUrlPolicy());
        var wordpress = new WordPressFeedCollector(fetcher, parser, new PublicHttpsFeedUrlPolicy(host -> new java.net.InetAddress[]{
                java.net.InetAddress.getByAddress(new byte[]{8, 8, 8, 8})}));
        var redditConfig = new RedditOAuthConfig(null, null, null, null, false);
        var redditTransport = new RedditHttpTransport() {
            @Override public RedditHttpResponse postForm(java.net.URI uri, java.util.Map<String, String> headers, String formBody) {
                return new RedditHttpResponse(200, "{}", java.util.Map.of());
            }
            @Override public RedditHttpResponse get(java.net.URI uri, java.util.Map<String, String> headers) {
                return new RedditHttpResponse(200, "{}", java.util.Map.of());
            }
        };
        var redditTokens = new RedditOAuthTokenProvider(redditConfig, redditTransport);
        var reddit = new RedditCollector(redditConfig, redditTokens, redditTransport);
        CollectorService service = new CollectorService(registry, news, alerts, wordpress, reddit);
        assertThrows(SourceAccessException.class, () -> service.collect(CollectorSource.ISSUU, "example"));
    }
}
