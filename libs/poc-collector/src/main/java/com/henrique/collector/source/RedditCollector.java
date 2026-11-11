package com.henrique.collector.source;

import com.fasterxml.jackson.databind.JsonNode;
import com.henrique.collector.feed.FeedItem;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class RedditCollector implements FeedCollector {
    private static final int MAX_POSTS = 100;
    private final RedditOAuthConfig config;
    private final RedditOAuthTokenProvider tokens;
    private final RedditHttpTransport transport;
    private final Clock clock;

    public RedditCollector(RedditOAuthConfig config, RedditOAuthTokenProvider tokens, RedditHttpTransport transport) {
        this(config, tokens, transport, Clock.systemUTC());
    }

    RedditCollector(RedditOAuthConfig config, RedditOAuthTokenProvider tokens,
                    RedditHttpTransport transport, Clock clock) {
        this.config = config;
        this.tokens = tokens;
        this.transport = transport;
        this.clock = clock;
    }

    @Override
    public List<FeedItem> collect(String subredditInput) {
        config.requireReady();
        String subreddit = validateSubreddit(subredditInput);
        URI uri = URI.create("https://oauth.reddit.com/r/" + subreddit + "/new?limit=" + MAX_POSTS);
        RedditHttpResponse response = transport.get(uri, Map.of(
                "Authorization", "Bearer " + tokens.accessToken(),
                "Accept", "application/json",
                "User-Agent", config.userAgent()));
        if (response.statusCode() == 429) throw new RedditApiException("Reddit Data API rate limit reached; retry later.");
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new RedditApiException("Reddit Data API request failed with HTTP " + response.statusCode() + ".");
        }
        return parseListing(response.body(), clock.instant());
    }

    public static String validateSubreddit(String input) {
        if (input == null) throw new IllegalArgumentException("A subreddit name is required.");
        String value = input.trim();
        if (value.startsWith("r/")) value = value.substring(2);
        if (!value.matches("[A-Za-z0-9_]{2,21}")) {
            throw new IllegalArgumentException("Subreddit name must be 2 to 21 letters, digits, or underscores.");
        }
        return value;
    }

    private static List<FeedItem> parseListing(String body, Instant retrievedAt) {
        try {
            JsonNode children = RedditJson.MAPPER.readTree(body).path("data").path("children");
            if (!children.isArray()) throw new RedditApiException("Reddit Data API returned an invalid post listing.");
            List<FeedItem> result = new ArrayList<>();
            for (JsonNode child : children) {
                if (result.size() >= MAX_POSTS) break;
                JsonNode data = child.path("data");
                String id = data.path("id").asText("");
                String title = data.path("title").asText("");
                String permalink = data.path("permalink").asText("");
                if (id.isBlank() || title.isBlank() || !permalink.startsWith("/r/")) continue;
                String postPath = permalink.substring(1);
                URI link = URI.create("https://www.reddit.com/" + postPath);
                Instant publishedAt = null;
                JsonNode createdUtc = data.get("created_utc");
                if (createdUtc != null && createdUtc.isNumber()) {
                    publishedAt = Instant.ofEpochMilli((long) (createdUtc.asDouble() * 1_000));
                }
                result.add(new FeedItem(CollectorSource.REDDIT, id, title, link, publishedAt, retrievedAt));
            }
            return List.copyOf(result);
        } catch (RedditApiException e) {
            throw e;
        } catch (Exception e) {
            throw new RedditApiException("Reddit Data API returned invalid JSON.");
        }
    }
}
