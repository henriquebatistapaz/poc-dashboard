package com.henrique.collector.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.henrique.collector.feed.RssFeedParser;
import com.henrique.collector.source.CollectorService;
import com.henrique.collector.source.GoogleAlertsCollector;
import com.henrique.collector.source.GoogleAlertsUrlPolicy;
import com.henrique.collector.source.GoogleNewsCollector;
import com.henrique.collector.source.PublicHttpsFeedUrlPolicy;
import com.henrique.collector.source.SourceRegistry;
import com.henrique.collector.source.WordPressFeedCollector;
import java.net.InetAddress;
import java.nio.file.Path;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CollectorSchedulerTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void collectsDueFeedPersistsResultsAndAdvancesNextRun() throws Exception {
        byte[] xml;
        try (var input = getClass().getResourceAsStream("/feeds/wordpress-atom.xml")) {
            xml = input.readAllBytes();
        }
        var resolver = (com.henrique.collector.source.DnsResolver) host ->
                new InetAddress[]{InetAddress.getByName("8.8.8.8")};
        var parser = new RssFeedParser();
        var fetcher = (com.henrique.collector.feed.FeedFetcher) uri -> xml;
        var registry = new SourceRegistry();
        var collector = new CollectorService(registry, new GoogleNewsCollector(fetcher, parser),
                new GoogleAlertsCollector(fetcher, parser, new GoogleAlertsUrlPolicy()),
                new WordPressFeedCollector(fetcher, parser, new PublicHttpsFeedUrlPolicy(resolver)), null);
        var store = new CollectorStateStore(temporaryDirectory.resolve("collector.json"));
        var secretBox = new SecretBox(new byte[32]);
        Instant dueAt = Instant.now().minusSeconds(5);
        ScheduledFeed scheduled = store.createFeed(com.henrique.collector.source.CollectorSource.WORDPRESS,
                secretBox.encrypt("https://blog.example/feed/atom"), 3_600, dueAt);
        var scheduler = new CollectorScheduler(collector, store, secretBox, 30);

        scheduler.tick();

        assertEquals(1, store.items(null, 10).size());
        ScheduledFeed updated = store.feeds().getFirst();
        assertEquals(scheduled.id(), updated.id());
        assertEquals("SUCCEEDED", updated.lastStatus());
        assertTrue(updated.nextRunAt().isAfter(Instant.now()));
    }
}
