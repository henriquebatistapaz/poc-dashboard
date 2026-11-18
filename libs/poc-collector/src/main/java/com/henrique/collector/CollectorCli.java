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
            System.err.println("Usage: java -jar poc-collector.jar <google-news|wordpress|mastodon|lemmy|dev-to|hacker-news|stack-exchange|github|tabnews> [query]");
            System.exit(2);
        }
        var fetcher = new JdkHttpFeedFetcher();
        var parser = new RssFeedParser();
        var service = new CollectorService(new SourceRegistry(),
                new GoogleNewsCollector(fetcher, parser),
                new WordPressFeedCollector(fetcher, parser));
        try {
            CollectorSource source = switch (args[0].toLowerCase()) {
                case "google-news" -> CollectorSource.GOOGLE_NEWS;
                case "wordpress" -> CollectorSource.WORDPRESS;
                case "mastodon" -> CollectorSource.MASTODON;
                case "lemmy" -> CollectorSource.LEMMY;
                case "dev-to" -> CollectorSource.DEV_TO;
                case "hacker-news" -> CollectorSource.HACKER_NEWS;
                case "stack-exchange" -> CollectorSource.STACK_EXCHANGE;
                case "github" -> CollectorSource.GITHUB;
                case "tabnews" -> CollectorSource.TABNEWS;
                case "issuu" -> CollectorSource.ISSUU;
                default -> throw new IllegalArgumentException("Unknown or unavailable source.");
            };
            String input = switch (source) {
                case GOOGLE_NEWS, WORDPRESS, MASTODON, LEMMY, DEV_TO, HACKER_NEWS, STACK_EXCHANGE, GITHUB, TABNEWS ->
                        String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length));
                default -> "";
            };
            List<FeedItem> items = service.collect(source, input);
            System.out.println(FeedItemJson.encode(items));
        } catch (RuntimeException e) {
            System.err.println(e.getMessage());
            System.exit(1);
        }
    }
}
