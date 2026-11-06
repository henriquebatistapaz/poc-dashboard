package com.henrique.collector.source;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RedditCollectorTest {
    private RedditOAuthConfig config(boolean approved) {
        return new RedditOAuthConfig("client-id", "client-secret", "refresh-token", "reader_user", approved);
    }

    @Test
    void fetchesOnlyNewPostsAndMapsMinimalMetadata() {
        FakeRedditHttpTransport transport = new FakeRedditHttpTransport();
        transport.getResponse = new RedditHttpResponse(200,
                "{\"data\":{\"children\":[{\"data\":{\"id\":\"abc123\",\"title\":\"Sample post\","
                        + "\"permalink\":\"/r/java/comments/abc123/sample/\",\"created_utc\":1793870400.0,"
                        + "\"selftext\":\"must not be retained\"}}]}}", Map.of());
        RedditOAuthConfig config = config(true);
        RedditOAuthTokenProvider tokens = new RedditOAuthTokenProvider(config, transport);
        RedditCollector collector = new RedditCollector(config, tokens, transport,
                Clock.fixed(Instant.parse("2026-11-05T13:00:00Z"), ZoneOffset.UTC));

        var items = collector.collect("r/java");

        assertEquals(1, items.size());
        assertEquals("abc123", items.getFirst().sourceId());
        assertEquals("Sample post", items.getFirst().title());
        assertEquals("https://www.reddit.com/r/java/comments/abc123/sample/", items.getFirst().link().toString());
        assertEquals(CollectorSource.REDDIT, items.getFirst().source());
        assertEquals(Instant.parse("2026-11-05T13:00:00Z"), items.getFirst().retrievedAt());
        assertEquals("https://oauth.reddit.com/r/java/new?limit=100", transport.lastUri.toString());
        assertEquals("Bearer access-123", transport.lastHeaders.get("Authorization"));
        assertTrue(transport.lastHeaders.get("User-Agent").contains("by /u/reader_user"));
        assertEquals(2, transport.calls.size()); // token refresh then one Data API read
    }

    @Test
    void refusesNetworkWhenAccessApprovalOrCredentialsAreMissing() {
        FakeRedditHttpTransport transport = new FakeRedditHttpTransport();
        RedditOAuthConfig config = config(false);
        RedditCollector collector = new RedditCollector(config,
                new RedditOAuthTokenProvider(config, transport), transport);
        assertThrows(SourceAccessException.class, () -> collector.collect("java"));
        assertTrue(transport.calls.isEmpty());

        RedditOAuthConfig missing = new RedditOAuthConfig(null, null, null, null, true);
        RedditCollector missingCredentials = new RedditCollector(missing,
                new RedditOAuthTokenProvider(missing, transport), transport);
        assertThrows(SourceAccessException.class, () -> missingCredentials.collect("java"));
        assertTrue(transport.calls.isEmpty());
    }

    @Test
    void validatesSubredditAndSupportsRPrefix() {
        assertEquals("java", RedditCollector.validateSubreddit("r/java"));
        assertEquals("dev_tools", RedditCollector.validateSubreddit("dev_tools"));
        assertThrows(IllegalArgumentException.class, () -> RedditCollector.validateSubreddit("../secret"));
        assertThrows(IllegalArgumentException.class, () -> RedditCollector.validateSubreddit("x".repeat(22)));
    }

    @Test
    void handlesApiErrorsWithoutReturningResponseBody() {
        FakeRedditHttpTransport transport = new FakeRedditHttpTransport();
        transport.getResponse = new RedditHttpResponse(429, "refresh-token private body", Map.of());
        RedditOAuthConfig config = config(true);
        RedditCollector collector = new RedditCollector(config,
                new RedditOAuthTokenProvider(config, transport), transport);
        RedditApiException error = assertThrows(RedditApiException.class, () -> collector.collect("java"));
        assertTrue(error.getMessage().contains("rate limit"));
        assertFalse(error.getMessage().contains("refresh-token"));
    }

    @Test
    void skipsMalformedOrNonPostRowsAndRejectsMalformedJson() {
        FakeRedditHttpTransport transport = new FakeRedditHttpTransport();
        transport.getResponse = new RedditHttpResponse(200,
                "{\"data\":{\"children\":[{\"data\":{\"id\":\"x\",\"title\":\"bad\",\"permalink\":\"https://evil.example/\"}}]}}", Map.of());
        RedditOAuthConfig config = config(true);
        RedditCollector collector = new RedditCollector(config,
                new RedditOAuthTokenProvider(config, transport), transport);
        assertTrue(collector.collect("java").isEmpty());

        transport.getResponse = new RedditHttpResponse(200, "not-json", Map.of());
        RedditCollector invalidJson = new RedditCollector(config,
                new RedditOAuthTokenProvider(config, transport), transport);
        assertThrows(RedditApiException.class, () -> invalidJson.collect("java"));
    }
}
