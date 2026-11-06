package com.henrique.collector.source;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RedditOAuthTokenProviderTest {
    private RedditOAuthConfig config(boolean approved) {
        return new RedditOAuthConfig("client-id", "client-secret", "refresh-token", "reader_user", approved);
    }

    @Test
    void exchangesRefreshTokenWithBasicAuthAndCachesAccessToken() {
        FakeRedditHttpTransport transport = new FakeRedditHttpTransport();
        RedditOAuthTokenProvider provider = new RedditOAuthTokenProvider(config(true), transport,
                Clock.fixed(Instant.parse("2026-11-05T12:00:00Z"), ZoneOffset.UTC));

        assertEquals("access-123", provider.accessToken());
        assertEquals("access-123", provider.accessToken());
        assertEquals(1, transport.calls.size());
        assertEquals("https://www.reddit.com/api/v1/access_token", transport.lastUri.toString());
        assertEquals("Basic " + Base64.getEncoder().encodeToString("client-id:client-secret".getBytes()),
                transport.lastHeaders.get("Authorization"));
        assertEquals("grant_type=refresh_token&refresh_token=refresh-token", transport.lastForm);
        assertTrue(transport.lastHeaders.get("User-Agent").contains("by /u/reader_user"));
    }

    @Test
    void failsClosedBeforeNetworkWhenDataApiAccessIsNotApproved() {
        FakeRedditHttpTransport transport = new FakeRedditHttpTransport();
        RedditOAuthTokenProvider provider = new RedditOAuthTokenProvider(config(false), transport);
        assertThrows(SourceAccessException.class, provider::accessToken);
        assertTrue(transport.calls.isEmpty());
    }

    @Test
    void doesNotEchoOAuthResponseBodyOnTokenFailure() {
        FakeRedditHttpTransport transport = new FakeRedditHttpTransport();
        transport.postResponse = new RedditHttpResponse(401, "secret refresh-token denied", Map.of());
        RedditOAuthTokenProvider provider = new RedditOAuthTokenProvider(config(true), transport);
        RedditApiException error = assertThrows(RedditApiException.class, provider::accessToken);
        assertFalse(error.getMessage().contains("refresh-token"));
    }

    @Test
    void rejectsMalformedTokenResponse() {
        FakeRedditHttpTransport transport = new FakeRedditHttpTransport();
        transport.postResponse = new RedditHttpResponse(200, "{\"access_token\":\"x\"}", Map.of());
        RedditOAuthTokenProvider provider = new RedditOAuthTokenProvider(config(true), transport);
        assertThrows(RedditApiException.class, provider::accessToken);
    }
}
