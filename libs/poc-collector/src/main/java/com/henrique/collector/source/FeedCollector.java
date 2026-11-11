package com.henrique.collector.source;

import com.henrique.collector.feed.FeedItem;
import java.util.List;

@FunctionalInterface
public interface FeedCollector {
    List<FeedItem> collect(String input);
}
