package dev.henrique.collector.feed;

import java.net.URI;

@FunctionalInterface
public interface FeedFetcher {
    byte[] fetch(URI uri);
}
