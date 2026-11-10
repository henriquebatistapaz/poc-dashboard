package com.henrique.collector.source;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;
import org.junit.jupiter.api.Test;

class RedditOAuthAuthorizationTest {
    private RedditOAuthConfig config(boolean approved) {
        return new RedditOAuthConfig("client-id", "client-secret", null, "reader_user", approved);
    }

    @Test
    void createsReadOnlyPermanentAuthorizationUrlAndRandomState() {
        String state = RedditOAuthAuthorization.newState();
        assertEquals(64, state.length());
        assertNotEquals(state, RedditOAuthAuthorization.newState());

        var uri = RedditOAuthAuthorization.authorizationUri(config(true), "https://localhost/callback", state);
        assertEquals("www.reddit.com", uri.getHost());
        assertTrue(uri.getRawQuery().contains("response_type=code"));
        assertTrue(uri.getRawQuery().contains("duration=permanent"));
        assertTrue(uri.getRawQuery().contains("scope=read"));
        assertTrue(uri.getRawQuery().contains("redirect_uri=https%3A%2F%2Flocalhost%2Fcallback"));
        assertFalse(uri.getRawQuery().contains("scope=read+identity"));
    }

    @Test
    void exchangesAuthorizationCodeAndRedactsCredentialStringRepresentations() {
        FakeRedditHttpTransport transport = new FakeRedditHttpTransport();
        transport.postResponse = new RedditHttpResponse(200,
                "{\"access_token\":\"access-secret\",\"refresh_token\":\"refresh-secret\",\"token_type\":\"bearer\",\"expires_in\":3600}",
                Map.of());

        RedditOAuthTokens tokens = RedditOAuthAuthorization.exchangeAuthorizationCode(
                config(true), transport, "one-time-code", "http://localhost:8081/callback", "s".repeat(32), "s".repeat(32));

        assertEquals("https://www.reddit.com/api/v1/access_token", transport.lastUri.toString());
        assertTrue(transport.lastForm.contains("grant_type=authorization_code"));
        assertTrue(transport.lastForm.contains("code=one-time-code"));
        assertTrue(transport.lastForm.contains("redirect_uri=http%3A%2F%2Flocalhost%3A8081%2Fcallback"));
        assertEquals("access-secret", tokens.accessToken());
        assertEquals("refresh-secret", tokens.refreshToken());
        assertFalse(tokens.toString().contains("access-secret"));
        assertFalse(tokens.toString().contains("refresh-secret"));
        assertFalse(config(true).toString().contains("client-secret"));
    }

    @Test
    void requiresApprovalAndSafeRedirectUriBeforeNetworkCalls() {
        FakeRedditHttpTransport transport = new FakeRedditHttpTransport();
        assertThrows(SourceAccessException.class,
                () -> RedditOAuthAuthorization.authorizationUri(config(false), "https://localhost/callback", "s".repeat(32)));
        assertThrows(IllegalArgumentException.class,
                () -> RedditOAuthAuthorization.authorizationUri(config(true), "https://user@example.org/callback", "s".repeat(32)));
        assertThrows(IllegalArgumentException.class,
                () -> RedditOAuthAuthorization.exchangeAuthorizationCode(config(true), transport, "code", "http://example.org/callback", "s".repeat(32), "s".repeat(32)));
        assertThrows(IllegalArgumentException.class,
                () -> RedditOAuthAuthorization.exchangeAuthorizationCode(config(true), transport, "code", "http://localhost/callback", "s".repeat(32), "x".repeat(32)));
        assertTrue(transport.calls.isEmpty());
    }

    @Test
    void requiresBothTokensFromPermanentAuthorizationResponse() {
        FakeRedditHttpTransport transport = new FakeRedditHttpTransport();
        transport.postResponse = new RedditHttpResponse(200,
                "{\"access_token\":\"access\",\"token_type\":\"bearer\",\"expires_in\":3600}", Map.of());
        assertThrows(RedditApiException.class, () -> RedditOAuthAuthorization.exchangeAuthorizationCode(
                config(true), transport, "code", "http://localhost/callback", "s".repeat(32), "s".repeat(32)));
    }
}
