package com.henrique.collector.source;

public final class RedditOAuthTokens {
    private final String accessToken;
    private final String refreshToken;
    private final long expiresInSeconds;

    RedditOAuthTokens(String accessToken, String refreshToken, long expiresInSeconds) {
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
        this.expiresInSeconds = expiresInSeconds;
    }

    public String accessToken() { return accessToken; }
    public String refreshToken() { return refreshToken; }
    public long expiresInSeconds() { return expiresInSeconds; }

    @Override
    public String toString() {
        return "RedditOAuthTokens[accessToken=[REDACTED], refreshToken=[REDACTED], expiresInSeconds=%d]"
                .formatted(expiresInSeconds);
    }
}
