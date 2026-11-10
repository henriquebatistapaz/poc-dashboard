package com.henrique.collector.source;

import com.henrique.collector.feed.FeedFetcher;
import com.henrique.collector.feed.FeedItem;
import com.henrique.collector.feed.RssFeedParser;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

public final class GoogleNewsCollector implements FeedCollector {
    private static final String FEED_BASE = "https://news.google.com/rss/search";
    private final FeedFetcher fetcher;
    private final RssFeedParser parser;
    private final String language;
    private final String region;
    private final String edition;

    public GoogleNewsCollector(FeedFetcher fetcher, RssFeedParser parser) {
        this(fetcher, parser, "en-US", "US", "US:en");
    }

    public GoogleNewsCollector(FeedFetcher fetcher, RssFeedParser parser, String language, String region, String edition) {
        this.fetcher = fetcher;
        this.parser = parser;
        this.language = requireLocale(language, "language", "[a-zA-Z]{2,3}(-[a-zA-Z]{2,4})?");
        this.region = requireLocale(region, "region", "[a-zA-Z]{2}");
        this.edition = requireLocale(edition, "edition", "[a-zA-Z]{2}:[a-zA-Z]{2,4}");
    }

    @Override
    public List<FeedItem> collect(String query) {
        if (query == null || query.isBlank() || query.length() > 200) {
            throw new IllegalArgumentException("Google News query must contain 1 to 200 characters.");
        }
        URI uri = URI.create(FEED_BASE + "?q=" + URLEncoder.encode(query.trim(), StandardCharsets.UTF_8)
                + "&hl=" + language + "&gl=" + region + "&ceid=" + edition);
        return parser.parse(fetcher.fetch(uri), CollectorSource.GOOGLE_NEWS);
    }

    public URI feedUri(String query) {
        if (query == null || query.isBlank() || query.length() > 200) {
            throw new IllegalArgumentException("Google News query must contain 1 to 200 characters.");
        }
        return URI.create(FEED_BASE + "?q=" + URLEncoder.encode(query.trim(), StandardCharsets.UTF_8)
                + "&hl=" + language + "&gl=" + region + "&ceid=" + edition);
    }

    private static String requireLocale(String value, String field, String pattern) {
        if (value == null || !value.matches(pattern)) throw new IllegalArgumentException("Invalid Google News " + field + ".");
        return value;
    }
}
