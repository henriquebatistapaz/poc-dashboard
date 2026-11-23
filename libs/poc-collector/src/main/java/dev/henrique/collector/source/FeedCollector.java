package dev.henrique.collector.source;

import dev.henrique.collector.feed.FeedItem;
import java.util.List;

@FunctionalInterface
public interface FeedCollector {
    List<FeedItem> collect(String input);
}
