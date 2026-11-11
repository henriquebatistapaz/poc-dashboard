package com.henrique.collector;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.henrique.collector.service.CollectorStateStore;
import com.henrique.collector.service.SecretBox;
import com.henrique.collector.source.CollectorService;
import com.henrique.collector.source.SourceRegistry;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CollectorApiServerTest {
    private static final String API_KEY = "test-api-key-that-is-longer-than-32-bytes";
    private static final byte[] ENCRYPTION_KEY = new byte[32];

    @TempDir
    Path temporaryDirectory;

    @Test
    void exposesOpenApiAndProtectsAndManagesScheduledFeeds() throws Exception {
        var registry = new SourceRegistry();
        var collector = new CollectorService(registry, null, null, null, null);
        var store = new CollectorStateStore(temporaryDirectory.resolve("collector.json"));
        var secretBox = new SecretBox(ENCRYPTION_KEY);
        try (var api = new CollectorApiServer(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0),
                collector, store, secretBox, API_KEY, 30)) {
            api.start();
            URI base = URI.create("http://127.0.0.1:" + api.address().getPort());
            HttpClient client = HttpClient.newHttpClient();

            HttpResponse<String> health = client.send(HttpRequest.newBuilder(base.resolve("/health")).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(200, health.statusCode());
            assertTrue(new ObjectMapper().readTree(health.body()).path("status").asText().equals("UP"));

            HttpResponse<String> openapi = client.send(HttpRequest.newBuilder(base.resolve("/openapi.json")).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(200, openapi.statusCode());
            assertTrue(new ObjectMapper().readTree(openapi.body()).path("paths").has("/v1/feeds"));

            HttpResponse<String> unauthorized = client.send(
                    HttpRequest.newBuilder(base.resolve("/v1/sources")).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(401, unauthorized.statusCode());

            HttpResponse<String> sources = authorized(client, base.resolve("/v1/sources"), "GET", null);
            assertEquals(200, sources.statusCode());
            assertTrue(sources.body().contains("\"source\":\"ISSUU\",\"status\":\"UNAVAILABLE\""));

            HttpResponse<String> created = authorized(client, base.resolve("/v1/feeds"), "POST",
                    "{\"source\":\"WORDPRESS\",\"input\":\"https://example.org/feed/\",\"intervalSeconds\":3600}");
            assertEquals(201, created.statusCode());
            String id = new ObjectMapper().readTree(created.body()).path("id").asText();
            assertFalse(id.isBlank());
            assertTrue(store.feeds().getFirst().encryptedInput().contains("https://") == false);

            HttpResponse<String> scheduled = authorized(client, base.resolve("/v1/feeds"), "GET", null);
            assertEquals(200, scheduled.statusCode());
            assertTrue(scheduled.body().contains(id));

            HttpResponse<String> deleted = authorized(client, base.resolve("/v1/feeds/" + id), "DELETE", null);
            assertEquals(204, deleted.statusCode());
            assertTrue(store.feeds().isEmpty());

            HttpResponse<String> disabledIssuu = authorized(client, base.resolve("/v1/feeds"), "POST",
                    "{\"source\":\"ISSUU\",\"input\":\"https://example.org/feed/\"}");
            assertEquals(422, disabledIssuu.statusCode());
        }
    }

    private static HttpResponse<String> authorized(HttpClient client, URI uri, String method, String body)
            throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(uri).header("Authorization", "Bearer " + API_KEY);
        if ("POST".equals(method)) {
            request.header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body));
        } else if ("DELETE".equals(method)) {
            request.DELETE();
        } else {
            request.GET();
        }
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }
}
