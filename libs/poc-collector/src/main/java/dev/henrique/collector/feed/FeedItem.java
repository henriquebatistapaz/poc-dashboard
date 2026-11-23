package dev.henrique.collector.feed;

import dev.henrique.collector.source.CollectorSource;
import java.net.URI;
import java.time.Instant;

public record FeedItem(CollectorSource source, String sourceId, String title, URI link,
                       Instant publishedAt, Instant retrievedAt) { }
