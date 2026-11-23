package dev.henrique.collector.source;

import static org.junit.jupiter.api.Assertions.*;

import dev.henrique.collector.feed.RssFeedParser;
import org.junit.jupiter.api.Test;

class SourceRegistryTest {
    @Test
    void describesCapabilityOfEveryRequestedSource() {
        SourceRegistry registry = new SourceRegistry();
        assertEquals(13, registry.all().size());
        assertEquals(CapabilityStatus.CONDITIONAL, registry.get(CollectorSource.GOOGLE_NEWS).status());
        assertEquals(CapabilityStatus.AVAILABLE, registry.get(CollectorSource.WORDPRESS).status());
        assertEquals(CapabilityStatus.UNAVAILABLE, registry.get(CollectorSource.ISSUU).status());
        for (CollectorSource source : new CollectorSource[]{CollectorSource.MASTODON, CollectorSource.LEMMY,
                CollectorSource.DEV_TO, CollectorSource.HACKER_NEWS, CollectorSource.STACK_EXCHANGE,
                CollectorSource.GITHUB, CollectorSource.TABNEWS, CollectorSource.WIKIPEDIA,
                CollectorSource.GITLAB, CollectorSource.LOBSTERS}) {
            assertEquals(CapabilityStatus.AVAILABLE, registry.get(source).status());
        }
    }

    @Test
    void refusesCollectorsWithoutPermittedAccess() {
        SourceRegistry registry = new SourceRegistry();
        var fetcher = (dev.henrique.collector.feed.FeedFetcher) uri -> new byte[0];
        var parser = new RssFeedParser();
        var news = new GoogleNewsCollector(fetcher, parser);
        var wordpress = new WordPressFeedCollector(fetcher, parser);
        CollectorService service = new CollectorService(registry, news, wordpress);
        assertThrows(SourceAccessException.class, () -> service.collect(CollectorSource.ISSUU, "example"));
    }
}
