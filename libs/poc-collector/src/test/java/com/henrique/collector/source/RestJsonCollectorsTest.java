package com.henrique.collector.source;

import static org.junit.jupiter.api.Assertions.*;

import com.sun.net.httpserver.HttpServer;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.GZIPOutputStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RestJsonCollectorsTest {
    private HttpServer server;
    private String response;
    private int responseCode = 200;
    private String contentEncoding;
    private final AtomicInteger requests = new AtomicInteger();
    private String requestPath;
    private String acceptHeader;
    private boolean delayResponse;

    @BeforeEach void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            requests.incrementAndGet();
            requestPath = exchange.getRequestURI().toString();
            acceptHeader = exchange.getRequestHeaders().getFirst("Accept");
            if (delayResponse) {
                try { Thread.sleep(11_000); }
                catch (InterruptedException exception) { Thread.currentThread().interrupt(); }
            }
            byte[] body = response.getBytes(StandardCharsets.UTF_8);
            if (contentEncoding != null && contentEncoding.equals("gzip")) {
                body = gzip(body);
                exchange.getResponseHeaders().set("Content-Encoding", "gzip");
            }
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(responseCode, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        response = "[]";
    }

    @AfterEach void stopServer() { server.stop(0); }

    @Test void parsesAllSevenSourcesAndBuildsExpectedRequests() {
        response = """
                [{"id":"m1","content":"<p>Java <b>JDK 27</b> &amp; tests</p><script>alert(1)</script>","url":null,"uri":"https://mastodon.social/status/1","created_at":"2026-11-18T12:00:00Z"}]
                """;
        var mastodon = new MastodonCollector(endpoint("/mastodon" )).collect("#java").getFirst();
        assertEquals("Java JDK 27 & tests", mastodon.title());
        assertEquals("https://mastodon.social/status/1", mastodon.link().toString());
        assertTrue(requestPath.startsWith("/mastodon/java?limit=40"));

        response = """
{"posts":[{"post":{"id":42,"name":"Lemmy post","url":null,"ap_id":"https://lemmy.world/post/42","published":"2026-11-18T12:00:00Z"}}]}
""";
        var lemmy = new LemmyCollector(endpoint("/lemmy")).collect("linux").getFirst();
        assertEquals("42", lemmy.sourceId());
        assertEquals("https://lemmy.world/post/42", lemmy.link().toString());
        assertTrue(requestPath.contains("q=linux"));

        response = """
[{"id":7,"title":"DEV article","url":"","canonical_url":"https://dev.to/a/7","published_at":"2026-11-18T12:00:00Z"}]
""";
        var devTo = new DevToCollector(endpoint("/devto")).collect("@alice").getFirst();
        assertEquals("DEV article", devTo.title());
        assertTrue(requestPath.contains("username=alice"));

        response = """
{"hits":[{"objectID":"hn-1","title":"HN title","url":null,"created_at":"2026-11-18T12:00:00Z"}]}
""";
        var hackerNews = new HackerNewsCollector(endpoint("/hn")).collect("java").getFirst();
        assertEquals("https://news.ycombinator.com/item?id=hn-1", hackerNews.link().toString());
        assertTrue(requestPath.contains("tags=story"));

        response = """
{"items":[{"question_id":42,"title":"Why &quot;Java&quot; &amp; C#?","link":"https://stackoverflow.com/q/42","creation_date":1795000000}]}
""";
        contentEncoding = "gzip";
        var stackExchange = new StackExchangeCollector(endpoint("/stack")).collect("java").getFirst();
        assertEquals("Why \"Java\" & C#?", stackExchange.title());
        assertEquals(1795000000L, stackExchange.publishedAt().getEpochSecond());

        contentEncoding = null;
        response = """
{"items":[{"id":9,"full_name":"team/project","description":"A repository","html_url":"https://github.com/team/project","updated_at":"2026-11-18T12:00:00Z"}]}
""";
        var github = new GitHubCollector(endpoint("/github")).collect("java").getFirst();
        assertEquals("team/project — A repository", github.title());
        assertTrue(requestPath.contains("sort=updated"));
        assertEquals("application/vnd.github+json", serverRequestHeader("/github"));

        response = """
[{"id":"tab-1","owner_username":"dev","slug":"post","title":"Java post","published_at":"2026-11-18T12:00:00Z"},{"id":"tab-2","owner_username":"dev","slug":"css","title":"CSS post"}]
""";
        var tabNews = new TabNewsCollector(endpoint("/tabnews")).collect("java");
        assertEquals(1, tabNews.size());
        assertEquals("https://www.tabnews.com.br/dev/post", tabNews.getFirst().link().toString());
        assertTrue(requestPath.contains("per_page=100"));
        assertEquals(7, requests.get());
    }

    @Test void rejectsInvalidInputsBeforeSendingRequests() {
        assertThrows(IllegalArgumentException.class, () -> new MastodonCollector(endpoint("/mastodon")).collect("#"));
        assertThrows(IllegalArgumentException.class, () -> new DevToCollector(endpoint("/devto")).collect("@"));
        assertThrows(IllegalArgumentException.class, () -> new TabNewsCollector(endpoint("/tabnews")).collect("@bad/name"));
        assertEquals(0, requests.get());
    }

    @Test void rejectsOverlongInputAndNonOfficialHost() {
        assertThrows(IllegalArgumentException.class,
                () -> new GitHubCollector(endpoint("/github")).collect("x".repeat(201)));
        assertThrows(IllegalArgumentException.class,
                () -> new GitHubCollector(URI.create("https://attacker.example/search")).collect("java"));
        assertEquals(0, requests.get());
    }

    @Test void truncatesTitlesAfterHtmlCleanup() {
        response = "[{\"id\":\"m2\",\"content\":\"<p>" + "x".repeat(1_001)
                + "</p>\",\"url\":\"https://mastodon.social/status/2\"}]";
        var item = new MastodonCollector(endpoint("/mastodon")).collect("java").getFirst();
        assertEquals(1_000, item.title().length());
    }

    @Test void skipsDangerousItemsAndHandlesEmptyLists() {
        response = """
{"hits":[{"objectID":"bad","title":"Bad","url":"javascript:alert(1)"},{"objectID":"ok","title":"Good","url":"https://example.test/good"}]}
""";
        var items = new HackerNewsCollector(endpoint("/hn")).collect("safe");
        assertEquals(1, items.size());
        assertEquals("ok", items.getFirst().sourceId());
        response = "{" + "\"hits\":[]}";
        assertTrue(new HackerNewsCollector(endpoint("/hn")).collect("empty").isEmpty());
    }

    @Test void failsSafelyForHttpErrorsAndMalformedRootWithoutLeakingInputOrBody() {
        responseCode = 429;
        response = "rate limit: secret-term";
        SourceAccessException httpError = assertThrows(SourceAccessException.class,
                () -> new GitHubCollector(endpoint("/github")).collect("secret-term"));
        assertFalse(httpError.getMessage().contains("secret-term"));
        assertFalse(httpError.getMessage().contains("rate limit"));
        responseCode = 200;
        response = "{not-json";
        assertThrows(SourceAccessException.class,
                () -> new GitHubCollector(endpoint("/github")).collect("secret-term"));
        response = "{}";
        assertThrows(SourceAccessException.class,
                () -> new GitHubCollector(endpoint("/github")).collect("secret-term"));
    }

    @Test void timesOutSafelyWithoutLeakingTheSearchTerm() {
        delayResponse = true;
        SourceAccessException error = assertThrows(SourceAccessException.class,
                () -> new GitHubCollector(endpoint("/github")).collect("private-search-term"));
        assertFalse(error.getMessage().contains("private-search-term"));
    }

    @Test void enforcesDecompressedResponseLimit() {
        response = "x".repeat(RestJsonClient.MAX_RESPONSE_BYTES + 1);
        contentEncoding = "gzip";
        SourceAccessException error = assertThrows(SourceAccessException.class,
                () -> new GitHubCollector(endpoint("/github")).collect("large"));
        assertFalse(error.getMessage().contains("large"));
        contentEncoding = null;
        response = "x".repeat(RestJsonClient.MAX_RESPONSE_BYTES + 1);
        assertThrows(SourceAccessException.class,
                () -> new GitHubCollector(endpoint("/github")).collect("large"));
    }

    private URI endpoint(String path) { return URI.create("http://127.0.0.1:" + server.getAddress().getPort() + path); }

    private String serverRequestHeader(String path) {
        return acceptHeader;
    }

    private static byte[] gzip(byte[] bytes) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(output)) { gzip.write(bytes); }
        return output.toByteArray();
    }
}
