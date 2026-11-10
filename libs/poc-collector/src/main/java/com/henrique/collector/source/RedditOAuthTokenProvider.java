package com.henrique.collector.source;

import com.fasterxml.jackson.databind.JsonNode;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;

public final class RedditOAuthTokenProvider {
    private static final URI TOKEN_ENDPOINT = URI.create("https://www.reddit.com/api/v1/access_token");
    private final RedditOAuthConfig config;
    private final RedditHttpTransport transport;
    private final Clock clock;
    private String cachedAccessToken;
    private Instant expiresAt = Instant.MIN;

    public RedditOAuthTokenProvider(RedditOAuthConfig config, RedditHttpTransport transport) {
        this(config, transport, Clock.systemUTC());
    }

    RedditOAuthTokenProvider(RedditOAuthConfig config, RedditHttpTransport transport, Clock clock) {
        this.config = config;
        this.transport = transport;
        this.clock = clock;
    }

    public synchronized String accessToken() {
        config.requireReady();
        if (cachedAccessToken != null && clock.instant().isBefore(expiresAt)) return cachedAccessToken;

        String credentials = config.clientId() + ":" + config.clientSecret();
        String basicAuth = Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
        String form = "grant_type=refresh_token&refresh_token=" + encode(config.refreshToken());
        RedditHttpResponse response = transport.postForm(TOKEN_ENDPOINT, Map.of(
                "Authorization", "Basic " + basicAuth,
                "Content-Type", "application/x-www-form-urlencoded",
                "Accept", "application/json",
                "User-Agent", config.userAgent()), form);
        if (response.statusCode() != 200) {
            throw new RedditApiException("Reddit OAuth token refresh failed with HTTP " + response.statusCode() + ".");
        }
        try {
            JsonNode json = RedditJson.MAPPER.readTree(response.body());
            String token = json.path("access_token").asText("");
            long expiresIn = json.path("expires_in").asLong(0);
            if (token.isBlank() || expiresIn <= 0 || !json.path("token_type").asText("").equalsIgnoreCase("bearer")) {
                throw new RedditApiException("Reddit OAuth returned an incomplete token response.");
            }
            cachedAccessToken = token;
            expiresAt = clock.instant().plusSeconds(Math.max(1, expiresIn - 60));
            return cachedAccessToken;
        } catch (RedditApiException e) {
            throw e;
        } catch (Exception e) {
            throw new RedditApiException("Reddit OAuth returned an invalid token response.");
        }
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
