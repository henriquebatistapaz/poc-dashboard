package com.henrique.collector.source;

import com.henrique.collector.feed.FeedItem;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public final class CollectorService {
    private final SourceRegistry registry;
    private final Map<CollectorSource, FeedCollector> collectors = new EnumMap<>(CollectorSource.class);

    public CollectorService(SourceRegistry registry, GoogleNewsCollector googleNews,
                            GoogleAlertsCollector googleAlerts, WordPressFeedCollector wordpress,
                            RedditCollector reddit) {
        this.registry = registry;
        collectors.put(CollectorSource.GOOGLE_NEWS, googleNews);
        collectors.put(CollectorSource.GOOGLE_ALERTS, googleAlerts);
        collectors.put(CollectorSource.WORDPRESS, wordpress);
        collectors.put(CollectorSource.REDDIT, reddit);
    }

    public List<FeedItem> collect(CollectorSource source, String input) {
        SourceCapability capability = registry.get(source);
        if (capability.status() == CapabilityStatus.UNAVAILABLE || capability.status() == CapabilityStatus.UNVERIFIED) {
            throw new SourceAccessException(source + " collector is disabled: " + capability.reason());
        }
        FeedCollector collector = collectors.get(source);
        if (collector == null) throw new SourceAccessException(source + " collector is not implemented.");
        return collector.collect(input);
    }
}
