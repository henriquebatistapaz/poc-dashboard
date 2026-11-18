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
            System.err.println("Usage: java -jar poc-collector.jar <google-news|wordpress> [query]");
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
                case "issuu" -> CollectorSource.ISSUU;
                default -> throw new IllegalArgumentException("Unknown source. Use google-news, wordpress, or issuu.");
            };
            String input = switch (source) {
                case GOOGLE_NEWS, WORDPRESS -> String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length));
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
