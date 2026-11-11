package com.henrique.collector.source;

import com.henrique.collector.feed.FeedFetcher;
import com.henrique.collector.feed.FeedItem;
import com.henrique.collector.feed.RssFeedParser;
import java.net.URI;
import java.util.List;

public final class WordPressFeedCollector implements FeedCollector {
    private final FeedFetcher fetcher;
    private final RssFeedParser parser;
    private final PublicHttpsFeedUrlPolicy urlPolicy;

    public WordPressFeedCollector(FeedFetcher fetcher, RssFeedParser parser, PublicHttpsFeedUrlPolicy urlPolicy) {
        this.fetcher = fetcher;
        this.parser = parser;
        this.urlPolicy = urlPolicy;
    }

    @Override
    public List<FeedItem> collect(String feedUrl) {
        URI uri = urlPolicy.validate(feedUrl);
        return parser.parse(fetcher.fetch(uri), CollectorSource.WORDPRESS);
    }
}
