package com.henrique.collector.source;

import com.fasterxml.jackson.databind.JsonNode;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;

/** Builds a user-consent URL and exchanges its one-time authorization code. */
public final class RedditOAuthAuthorization {
    private static final URI AUTHORIZATION_ENDPOINT = URI.create("https://www.reddit.com/api/v1/authorize");
    private static final URI TOKEN_ENDPOINT = URI.create("https://www.reddit.com/api/v1/access_token");
    private static final SecureRandom RANDOM = new SecureRandom();

    private RedditOAuthAuthorization() { }

    public static String newState() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    public static URI authorizationUri(RedditOAuthConfig config, String redirectUri, String state) {
        config.requireApprovedApp();
        URI redirect = validateRedirectUri(redirectUri);
        if (state == null || !state.matches("[A-Za-z0-9_-]{32,128}")) {
            throw new IllegalArgumentException("OAuth state must be a random URL-safe value of at least 32 characters.");
        }
        String query = "client_id=" + encode(config.clientId())
                + "&response_type=code&state=" + encode(state)
                + "&redirect_uri=" + encode(redirect.toString())
                + "&duration=permanent&scope=read";
        return URI.create(AUTHORIZATION_ENDPOINT + "?" + query);
    }

    public static RedditOAuthTokens exchangeAuthorizationCode(RedditOAuthConfig config,
                                                               RedditHttpTransport transport,
                                                               String code, String redirectUri,
                                                               String expectedState, String returnedState) {
        config.requireApprovedApp();
        if (code == null || code.isBlank() || code.length() > 4096) {
            throw new IllegalArgumentException("A valid Reddit OAuth authorization code is required.");
        }
        if (expectedState == null || returnedState == null
                || !expectedState.matches("[A-Za-z0-9_-]{32,128}")
                || !MessageDigest.isEqual(expectedState.getBytes(StandardCharsets.UTF_8),
                        returnedState.getBytes(StandardCharsets.UTF_8))) {
            throw new IllegalArgumentException("Reddit OAuth state validation failed.");
        }
        URI redirect = validateRedirectUri(redirectUri);
        String credentials = config.clientId() + ":" + config.clientSecret();
        String basicAuth = Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
        String form = "grant_type=authorization_code&code=" + encode(code)
                + "&redirect_uri=" + encode(redirect.toString());
        RedditHttpResponse response = transport.postForm(TOKEN_ENDPOINT, Map.of(
                "Authorization", "Basic " + basicAuth,
                "Content-Type", "application/x-www-form-urlencoded",
                "Accept", "application/json",
                "User-Agent", config.userAgent()), form);
        if (response.statusCode() != 200) {
            throw new RedditApiException("Reddit OAuth authorization-code exchange failed with HTTP " + response.statusCode() + ".");
        }
        try {
            JsonNode json = RedditJson.MAPPER.readTree(response.body());
            String accessToken = json.path("access_token").asText("");
            String refreshToken = json.path("refresh_token").asText("");
            long expiresIn = json.path("expires_in").asLong(0);
            if (accessToken.isBlank() || refreshToken.isBlank() || expiresIn <= 0
                    || !json.path("token_type").asText("").equalsIgnoreCase("bearer")) {
                throw new RedditApiException("Reddit OAuth did not return permanent authorization tokens.");
            }
            return new RedditOAuthTokens(accessToken, refreshToken, expiresIn);
        } catch (RedditApiException e) {
            throw e;
        } catch (Exception e) {
            throw new RedditApiException("Reddit OAuth returned an invalid authorization response.");
        }
    }

    private static URI validateRedirectUri(String value) {
        try {
            URI uri = URI.create(value);
            boolean localHttp = "http".equalsIgnoreCase(uri.getScheme())
                    && ("localhost".equalsIgnoreCase(uri.getHost()) || "127.0.0.1".equals(uri.getHost())
                    || "[::1]".equals(uri.getHost()));
            if (!uri.isAbsolute() || uri.getHost() == null || uri.getUserInfo() != null
                    || uri.getFragment() != null || !("https".equalsIgnoreCase(uri.getScheme()) || localHttp)) {
                throw new IllegalArgumentException("OAuth redirect URI must be HTTPS or a loopback HTTP URI without user info or fragments.");
            }
            return uri;
        } catch (NullPointerException | IllegalArgumentException e) {
            if (e instanceof IllegalArgumentException argumentException) throw argumentException;
            throw new IllegalArgumentException("A valid registered Reddit OAuth redirect URI is required.");
        }
    }

    private static String encode(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }
}
