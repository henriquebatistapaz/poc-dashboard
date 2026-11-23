package dev.henrique.collector.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.henrique.collector.feed.RssFeedParser;
import dev.henrique.collector.source.CollectorService;
import dev.henrique.collector.source.GoogleNewsCollector;
import dev.henrique.collector.source.SourceRegistry;
import dev.henrique.collector.source.WordPressFeedCollector;
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
        var parser = new RssFeedParser();
        var fetcher = (dev.henrique.collector.feed.FeedFetcher) uri -> xml;
        var registry = new SourceRegistry();
        var collector = new CollectorService(registry, new GoogleNewsCollector(fetcher, parser),
                new WordPressFeedCollector(fetcher, parser));
        var store = new CollectorStateStore(temporaryDirectory.resolve("collector.json"));
        var secretBox = new SecretBox(new byte[32]);
        Instant dueAt = Instant.now().minusSeconds(5);
        ScheduledFeed scheduled = store.createFeed(dev.henrique.collector.source.CollectorSource.WORDPRESS,
                secretBox.encrypt("java"), 3_600, dueAt);
        var scheduler = new CollectorScheduler(collector, store, secretBox, 30);

        scheduler.tick();

        assertEquals(1, store.items(null, 10).size());
        ScheduledFeed updated = store.feeds().getFirst();
        assertEquals(scheduled.id(), updated.id());
        assertEquals("SUCCEEDED", updated.lastStatus());
        assertTrue(updated.nextRunAt().isAfter(Instant.now()));
    }
}
