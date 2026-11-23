package dev.henrique.mastodon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MastodonWriteServiceTest {
    private static final String TOKEN = "token-escrita-123";
    private HttpServer server;
    private final List<String> methods = new CopyOnWriteArrayList<>();
    private final List<String> paths = new CopyOnWriteArrayList<>();
    private final List<String> bodies = new CopyOnWriteArrayList<>();
    private final List<String> authHeaders = new CopyOnWriteArrayList<>();
    private int status = 200;
    private String responseBody = "{}";

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            methods.add(exchange.getRequestMethod());
            paths.add(exchange.getRequestURI().getPath());
            authHeaders.add(exchange.getRequestHeaders().getFirst("Authorization"));
            bodies.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] bytes = responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
            if (bytes.length > 0) {
                try (OutputStream out = exchange.getResponseBody()) {
                    out.write(bytes);
                }
            } else {
                exchange.close();
            }
        });
        server.start();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private MastodonWriteService service() {
        URI instance = URI.create("http://127.0.0.1:" + server.getAddress().getPort());
        return new MastodonWriteService(new MastodonConfig(instance, TOKEN));
    }

    @Test
    void publishSendsStatusAndReturnsCreatedPost() {
        responseBody = "{\"id\":\"99\",\"url\":\"https://x.test/@me/99\",\"content\":\"<p>Olá &amp; oi</p>\","
                + "\"created_at\":\"2026-11-22T10:00:00Z\",\"account\":{\"acct\":\"me\"}}";
        MastodonPost post = service().publish("Olá & oi", "unlisted");

        assertEquals("POST", methods.get(0));
        assertEquals("/api/v1/statuses", paths.get(0));
        assertEquals("Bearer " + TOKEN, authHeaders.get(0));
        assertTrue(bodies.get(0).contains("status=Ol%C3%A1+%26+oi"));
        assertTrue(bodies.get(0).contains("visibility=unlisted"));
        assertEquals("99", post.id());
        assertEquals("Olá & oi", post.text());
        assertEquals("me", post.accountAcct());
    }

    @Test
    void publishDefaultsToPublicVisibility() {
        responseBody = "{\"id\":\"1\"}";
        service().publish("teste", null);
        assertTrue(bodies.get(0).contains("visibility=public"));
    }

    @Test
    void publishRejectsInvalidInputBeforeNetwork() {
        MastodonWriteService service = service();
        assertThrows(IllegalArgumentException.class, () -> service.publish("  ", "public"));
        assertThrows(IllegalArgumentException.class, () -> service.publish("x".repeat(501), "public"));
        assertThrows(IllegalArgumentException.class, () -> service.publish("ok", "fofoca"));
        assertTrue(methods.isEmpty());
    }

    @Test
    void favouriteUsesStatusPathAndReturnsFlag() {
        responseBody = "{\"id\":\"42\",\"favourited\":true}";
        assertTrue(service().favourite("42"));
        assertEquals("POST", methods.get(0));
        assertEquals("/api/v1/statuses/42/favourite", paths.get(0));
    }

    @Test
    void deleteUsesDeleteMethod() {
        responseBody = "{\"id\":\"42\"}";
        service().delete("42");
        assertEquals("DELETE", methods.get(0));
        assertEquals("/api/v1/statuses/42", paths.get(0));
    }

    @Test
    void invalidStatusIdIsRejectedBeforeNetwork() {
        MastodonWriteService service = service();
        assertThrows(IllegalArgumentException.class, () -> service.delete("../admin"));
        assertThrows(IllegalArgumentException.class, () -> service.favourite("1?x=y"));
        assertThrows(IllegalArgumentException.class, () -> service.favourite(null));
        assertTrue(methods.isEmpty());
    }

    @Test
    void forbiddenScopeErrorDoesNotLeakToken() {
        status = 403;
        responseBody = "{\"error\":\"insufficient scope\"}";
        MastodonException error = assertThrows(MastodonException.class, () -> service().delete("42"));
        assertFalse(error.getMessage().contains(TOKEN));
        assertTrue(error.getMessage().contains("403"));
    }
}
