package dev.henrique.collectorrunner.integration;

import dev.henrique.rsscollector.application.RssFeedLinkValidator;
import com.henrique.collector.feed.RssFeedParser;
import com.henrique.collector.source.*;
import java.nio.charset.StandardCharsets;
import java.net.InetSocketAddress;
import java.net.URI;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DefaultCollectionGatewayTest {
    static final String RSS = """
        <rss version="2.0"><channel><title>demo</title><item><guid>1</guid>
        <title>Integration title</title><link>https://example.org/1</link></item></channel></rss>
        """;
    static final String ATOM = """
        <feed xmlns="http://www.w3.org/2005/Atom"><id>feed</id><title>demo</title>
        <updated>2026-11-06T00:00:00Z</updated><entry><id>1</id><title>Atom title</title>
        <link href="https://example.org/atom"/><updated>2026-11-06T00:00:00Z</updated></entry></feed>
        """;

    private DefaultCollectionGateway gateway() {
        com.henrique.collector.feed.FeedFetcher fetcher = uri -> RSS.getBytes(StandardCharsets.UTF_8);
        var parser = new RssFeedParser();
        var collectors = new CollectorService(new SourceRegistry(), new GoogleNewsCollector(fetcher, parser),
                new WordPressFeedCollector(fetcher, parser));
        var urls = new RssFeedLinkValidator();
        return new DefaultCollectionGateway(collectors, url -> ATOM,
                new dev.henrique.rsscollector.application.RssFeedParser(urls), urls);
    }

    @Test
    void mapsEachImplementedFeedSource() {
        var gateway = gateway();
        assertEquals("google-news", gateway.collect(CollectionSource.GOOGLE_NEWS, "java").getFirst().source());
        assertEquals("wordpress", gateway.collect(CollectionSource.WORDPRESS, "java").getFirst().source());
        var rss = gateway.collect(CollectionSource.RSS, "https://example.org/feed").getFirst();
        assertEquals("rss", rss.source());
        assertEquals("Atom title", rss.title());
        assertNotEquals(rss.externalId(), gateway.collect(CollectionSource.RSS, "https://other.example.org/feed").getFirst().externalId());
    }

    @Test
    void reusesSocialAdaptersForSyntheticPosts() {
        var alpha = gateway().collect(CollectionSource.ALPHA, """
            {"posts":[{"id":"1","author":{"handle":"demo"},"text":"Alpha title",
            "created_at":"2026-11-06T00:00:00Z","url":"https://example.org/alpha"}]}
            """);
        var beta = gateway().collect(CollectionSource.BETA, """
            {"items":[{"postId":"1","authorName":"demo","body":"Beta title",
            "publishedAt":"2026-11-06T00:00:00Z","link":"https://example.org/beta"}]}
            """);
        assertEquals("alpha", alpha.getFirst().source());
        assertEquals("Alpha title", alpha.getFirst().title());
        assertEquals("beta", beta.getFirst().source());
        assertEquals("Beta title", beta.getFirst().title());
    }

    @Test
    void preservesSourceAvailabilityAndUrlRules() {
        var gateway = gateway();
        assertThrows(SourceAccessException.class, () -> gateway.collect(CollectionSource.ISSUU, "anything"));
        assertThrows(SourceAccessException.class, () -> DefaultCollectionGateway.fromEnvironment(Map.of()).collect(CollectionSource.ISSUU, "anything"));
        assertThrows(IllegalArgumentException.class, () -> gateway.collect(CollectionSource.WORDPRESS, "   "));
        assertThrows(IllegalArgumentException.class, () -> gateway.collect(CollectionSource.RSS, "http://127.0.0.1/feed"));
    }

    @Test
    void dispatchesEveryNewRestSourceThroughLocalFixtures() throws IOException {
        Map<String, String> bodies = Map.of(
                "/mastodon", "[{\"id\":\"m1\",\"content\":\"<p>Mastodon fixture</p>\",\"url\":\"https://mastodon.social/@x/1\"}]",
                "/lemmy", "{\"posts\":[{\"post\":{\"id\":1,\"name\":\"Lemmy fixture\",\"url\":\"https://lemmy.world/post/1\"}}]}",
                "/devto", "[{\"id\":2,\"title\":\"DEV fixture\",\"url\":\"https://dev.to/x/2\"}]",
                "/hn", "{\"hits\":[{\"objectID\":\"3\",\"title\":\"HN fixture\",\"url\":\"https://news.ycombinator.com/item?id=3\"}]}",
                "/stack", "{\"items\":[{\"question_id\":4,\"title\":\"Stack fixture\",\"link\":\"https://stackoverflow.com/q/4\"}]}",
                "/github", "{\"items\":[{\"id\":5,\"full_name\":\"org/repo\",\"html_url\":\"https://github.com/org/repo\"}]}",
                "/tabnews", "[{\"id\":\"6\",\"owner_username\":\"dev\",\"slug\":\"post\",\"title\":\"TabNews fixture\"}]",
                "/wiki", "{\"query\":{\"search\":[{\"pageid\":42,\"title\":\"Wikipedia fixture\"}]}}",
                "/gitlab", "[{\"id\":7,\"name_with_namespace\":\"org/project\",\"web_url\":\"https://gitlab.com/org/project\"}]",
                "/hottest.json", "[{\"short_id\":\"abc123\",\"title\":\"Lobsters fixture\",\"url\":\"https://example.test/story\"}]");
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            String path = exchange.getRequestURI().getPath();
            String key = path.startsWith("/mastodon/") ? "/mastodon" : path;
            byte[] body = bodies.getOrDefault(key, "{}").getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        try {
            URI base = URI.create("http://127.0.0.1:" + server.getAddress().getPort());
            Map<CollectionSource, URI> endpoints = Map.of(
                    CollectionSource.MASTODON, base.resolve("/mastodon"), CollectionSource.LEMMY, base.resolve("/lemmy"),
                    CollectionSource.DEV_TO, base.resolve("/devto"), CollectionSource.HACKER_NEWS, base.resolve("/hn"),
                    CollectionSource.STACK_EXCHANGE, base.resolve("/stack"), CollectionSource.GITHUB, base.resolve("/github"),
                    CollectionSource.TABNEWS, base.resolve("/tabnews"), CollectionSource.WIKIPEDIA, base.resolve("/wiki"),
                    CollectionSource.GITLAB, base.resolve("/gitlab"), CollectionSource.LOBSTERS, base);
            var gateway = DefaultCollectionGateway.fromEnvironment(Map.of(), endpoints);
            Map<CollectionSource, String> inputs = Map.of(
                    CollectionSource.MASTODON, "java", CollectionSource.LEMMY, "linux", CollectionSource.DEV_TO, "spring",
                    CollectionSource.HACKER_NEWS, "java", CollectionSource.STACK_EXCHANGE, "java",
                    CollectionSource.GITHUB, "java", CollectionSource.TABNEWS, "relevant", CollectionSource.WIKIPEDIA, "java",
                    CollectionSource.GITLAB, "java", CollectionSource.LOBSTERS, "hottest");
            Map<CollectionSource, String> slugs = Map.of(
                    CollectionSource.MASTODON, "mastodon", CollectionSource.LEMMY, "lemmy", CollectionSource.DEV_TO, "dev-to",
                    CollectionSource.HACKER_NEWS, "hacker-news", CollectionSource.STACK_EXCHANGE, "stack-exchange",
                    CollectionSource.GITHUB, "github", CollectionSource.TABNEWS, "tabnews", CollectionSource.WIKIPEDIA, "wikipedia",
                    CollectionSource.GITLAB, "gitlab", CollectionSource.LOBSTERS, "lobsters");
            for (CollectionSource source : CollectionSource.values()) {
                if (!inputs.containsKey(source)) continue;
                try {
                    var item = gateway.collect(source, inputs.get(source)).getFirst();
                    assertEquals(slugs.get(source), item.source());
                } catch (RuntimeException exception) {
                    throw new AssertionError("Local REST fixture failed for " + source, exception);
                }
            }
        } finally {
            server.stop(0);
        }
    }
}
