package com.henrique.collector.source;

import com.henrique.collector.feed.FeedItem;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public final class CollectorService {
    private final SourceRegistry registry;
    private final Map<CollectorSource, FeedCollector> collectors = new EnumMap<>(CollectorSource.class);

    public CollectorService(SourceRegistry registry, GoogleNewsCollector googleNews,
                            WordPressFeedCollector wordpress) {
        this(registry, googleNews, wordpress, new MastodonCollector(), new LemmyCollector(), new DevToCollector(),
                new HackerNewsCollector(), new StackExchangeCollector(), new GitHubCollector(), new TabNewsCollector(),
                new WikipediaCollector(), new GitLabCollector(), new LobstersCollector());
    }

    /** Backward-compatible constructor used by existing library clients and focused tests. */
    public CollectorService(SourceRegistry registry, GoogleNewsCollector googleNews,
                            WordPressFeedCollector wordpress, MastodonCollector mastodon, LemmyCollector lemmy,
                            DevToCollector devTo, HackerNewsCollector hackerNews,
                            StackExchangeCollector stackExchange, GitHubCollector github, TabNewsCollector tabNews) {
        this(registry, googleNews, wordpress, mastodon, lemmy, devTo, hackerNews, stackExchange, github, tabNews,
                new WikipediaCollector(), new GitLabCollector(), new LobstersCollector());
    }

    public CollectorService(SourceRegistry registry, GoogleNewsCollector googleNews,
                            WordPressFeedCollector wordpress, MastodonCollector mastodon, LemmyCollector lemmy,
                            DevToCollector devTo, HackerNewsCollector hackerNews,
                            StackExchangeCollector stackExchange, GitHubCollector github, TabNewsCollector tabNews,
                            WikipediaCollector wikipedia, GitLabCollector gitLab, LobstersCollector lobsters) {
        this.registry = registry;
        collectors.put(CollectorSource.GOOGLE_NEWS, googleNews);
        collectors.put(CollectorSource.WORDPRESS, wordpress);
        collectors.put(CollectorSource.MASTODON, mastodon);
        collectors.put(CollectorSource.LEMMY, lemmy);
        collectors.put(CollectorSource.DEV_TO, devTo);
        collectors.put(CollectorSource.HACKER_NEWS, hackerNews);
        collectors.put(CollectorSource.STACK_EXCHANGE, stackExchange);
        collectors.put(CollectorSource.GITHUB, github);
        collectors.put(CollectorSource.TABNEWS, tabNews);
        collectors.put(CollectorSource.WIKIPEDIA, wikipedia);
        collectors.put(CollectorSource.GITLAB, gitLab);
        collectors.put(CollectorSource.LOBSTERS, lobsters);
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

    public List<SourceCapability> sources() {
        return registry.all();
    }
}
