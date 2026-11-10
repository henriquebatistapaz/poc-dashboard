package com.henrique.collector;

import com.henrique.collector.feed.FeedItem;
import com.henrique.collector.feed.JdkHttpFeedFetcher;
import com.henrique.collector.feed.RssFeedParser;
import com.henrique.collector.source.*;
import java.util.List;

public final class CollectorCli {
    private CollectorCli() { }

    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java -jar poc-collector.jar <google-news|google-alerts|wordpress|reddit> [query-or-subreddit]");
            System.exit(2);
        }
        var fetcher = new JdkHttpFeedFetcher();
        var redditTransport = new JdkRedditHttpTransport();
        var redditConfig = RedditOAuthConfig.fromEnvironment(System.getenv());
        var parser = new RssFeedParser();
        var service = new CollectorService(new SourceRegistry(),
                new GoogleNewsCollector(fetcher, parser),
                new GoogleAlertsCollector(fetcher, parser, new GoogleAlertsUrlPolicy()),
                new WordPressFeedCollector(fetcher, parser, new PublicHttpsFeedUrlPolicy()),
                new RedditCollector(redditConfig, new RedditOAuthTokenProvider(redditConfig, redditTransport), redditTransport));
        try {
            CollectorSource source = switch (args[0].toLowerCase()) {
                case "google-news" -> CollectorSource.GOOGLE_NEWS;
                case "google-alerts" -> CollectorSource.GOOGLE_ALERTS;
                case "wordpress" -> CollectorSource.WORDPRESS;
                case "reddit" -> CollectorSource.REDDIT;
                case "issuu" -> CollectorSource.ISSUU;
                default -> throw new IllegalArgumentException("Unknown source. Use google-news, google-alerts, wordpress, reddit, or issuu.");
            };
            String input = switch (source) {
                case GOOGLE_NEWS -> String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length));
                case GOOGLE_ALERTS -> requiredEnvironment("GOOGLE_ALERTS_FEED_URL");
                case WORDPRESS -> requiredEnvironment("WORDPRESS_FEED_URL");
                case REDDIT -> args.length > 1 ? args[1] : requiredEnvironment("REDDIT_SUBREDDIT");
                default -> "";
            };
            List<FeedItem> items = service.collect(source, input);
            System.out.println(FeedItemJson.encode(items));
        } catch (RuntimeException e) {
            System.err.println(e.getMessage());
            System.exit(1);
        }
    }

    private static String requiredEnvironment(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " environment variable is required.");
        return value;
    }
}
