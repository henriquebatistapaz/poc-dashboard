package dev.henrique.collector.source;

import dev.henrique.collector.feed.FeedFetcher;
import dev.henrique.collector.feed.FeedItem;
import dev.henrique.collector.feed.RssFeedParser;
import java.net.URI;
import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

public final class WordPressFeedCollector implements FeedCollector {
    private static final String TAG_FEED_BASE = "https://wordpress.com/tag/";
    private static final Pattern COMBINING_MARKS = Pattern.compile("\\p{M}+");
    private static final Pattern NON_SLUG_CHARS = Pattern.compile("[^a-z0-9]+");
    private final FeedFetcher fetcher;
    private final RssFeedParser parser;

    public WordPressFeedCollector(FeedFetcher fetcher, RssFeedParser parser) {
        this.fetcher = fetcher;
        this.parser = parser;
    }

    @Override
    public List<FeedItem> collect(String query) {
        return parser.parse(fetcher.fetch(feedUri(query)), CollectorSource.WORDPRESS);
    }

    /** Normalizes a search keyword into a WordPress.com global tag RSS feed URI. */
    public URI feedUri(String query) {
        if (query == null || query.isBlank() || query.length() > 200) {
            throw new IllegalArgumentException("WordPress search query must contain 1 to 200 characters.");
        }
        String normalized = COMBINING_MARKS.matcher(Normalizer.normalize(query.trim(), Normalizer.Form.NFD))
                .replaceAll("")
                .toLowerCase(Locale.ROOT);
        String slug = NON_SLUG_CHARS.matcher(normalized).replaceAll("-").replaceAll("^-+|-+$", "");
        if (slug.isEmpty()) {
            throw new IllegalArgumentException("WordPress search query must contain alphanumeric characters.");
        }
        return URI.create(TAG_FEED_BASE + slug + "/feed/");
    }
}
