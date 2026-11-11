package com.henrique.collector.source;

import java.util.Map;

public record RedditOAuthConfig(String clientId, String clientSecret, String refreshToken,
                                String redditUsername, boolean dataApiApproved) {
    public static RedditOAuthConfig fromEnvironment(Map<String, String> environment) {
        return new RedditOAuthConfig(environment.get("REDDIT_CLIENT_ID"),
                environment.get("REDDIT_CLIENT_SECRET"), environment.get("REDDIT_REFRESH_TOKEN"),
                environment.get("REDDIT_USERNAME"),
                Boolean.parseBoolean(environment.getOrDefault("REDDIT_API_ACCESS_APPROVED", "false")));
    }

    public void requireReady() {
        requireApprovedApp();
        if (blank(refreshToken)) {
            throw new SourceAccessException("Reddit OAuth is not configured; a refresh token is required.");
        }
    }

    public void requireApprovedApp() {
        if (!dataApiApproved) {
            throw new SourceAccessException("Reddit collection is disabled until Reddit approves the Data API use case.");
        }
        if (blank(clientId) || blank(clientSecret) || blank(redditUsername)) {
            throw new SourceAccessException("Reddit OAuth is not configured; required credentials are missing.");
        }
        if (!redditUsername.matches("[A-Za-z0-9_-]{3,20}")) {
            throw new SourceAccessException("REDDIT_USERNAME must be the Reddit account username used in User-Agent.");
        }
    }

    public String userAgent() {
        if (blank(redditUsername)) throw new SourceAccessException("REDDIT_USERNAME is required for the Reddit User-Agent.");
        return "web:poc-collector:v0.1.0 (by /u/" + redditUsername + ")";
    }

    private static boolean blank(String value) { return value == null || value.isBlank(); }

    @Override
    public String toString() {
        return "RedditOAuthConfig[clientId=%s, clientSecret=[REDACTED], refreshToken=[REDACTED], redditUsername=%s, dataApiApproved=%s]"
                .formatted(blank(clientId) ? "[missing]" : "[configured]", redditUsername, dataApiApproved);
    }
}
