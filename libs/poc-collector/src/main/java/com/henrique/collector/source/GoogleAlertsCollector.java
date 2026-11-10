package com.henrique.collector.source;

import com.henrique.collector.feed.FeedFetcher;
import com.henrique.collector.feed.FeedItem;
import com.henrique.collector.feed.RssFeedParser;
import java.net.URI;
import java.util.List;

public final class GoogleAlertsCollector implements FeedCollector {
    private final FeedFetcher fetcher;
    private final RssFeedParser parser;
    private final GoogleAlertsUrlPolicy urlPolicy;

    public GoogleAlertsCollector(FeedFetcher fetcher, RssFeedParser parser, GoogleAlertsUrlPolicy urlPolicy) {
        this.fetcher = fetcher;
        this.parser = parser;
        this.urlPolicy = urlPolicy;
    }

    @Override
    public List<FeedItem> collect(String feedUrl) {
        URI validatedFeedUri = urlPolicy.validate(feedUrl);
        // Never include the URL in errors or logs: it grants access to a user's configured alert.
        try {
            return parser.parse(fetcher.fetch(validatedFeedUri), CollectorSource.GOOGLE_ALERTS);
        } catch (RuntimeException e) {
            throw new IllegalStateException("Google Alerts collection failed; the secret feed URL was omitted.");
        }
    }
}
