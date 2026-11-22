package com.henrique.mastodon;

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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MastodonSearchServiceTest {
    private static final String TOKEN = "segredo-de-teste-123";
    private HttpServer server;
    private final List<String> authHeaders = new CopyOnWriteArrayList<>();
    private final List<String> queries = new CopyOnWriteArrayList<>();
    private int status = 200;
    private String body = "{}";
    private String location;

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/v2/search", exchange -> {
            authHeaders.add(exchange.getRequestHeaders().getFirst("Authorization"));
            queries.add(exchange.getRequestURI().getRawQuery());
            if (location != null) exchange.getResponseHeaders().add("Location", location);
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        });
        server.start();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private MastodonSearchService service() {
        URI instance = URI.create("http://127.0.0.1:" + server.getAddress().getPort());
        return new MastodonSearchService(new MastodonConfig(instance, TOKEN));
    }

    @Test
    void returnsOnlyPublicUniquePostsWithPlainText() {
        body = """
                {"statuses":[
                  {"id":"1","visibility":"public","url":"https://x.test/@a/1","content":"<p>Olá &amp; <a href='x'>mundo</a></p>",
                   "created_at":"2026-11-20T10:00:00Z","account":{"acct":"a"}},
                  {"id":"2","visibility":"private","url":"https://x.test/@b/2","content":"secreto","account":{"acct":"b"}},
                  {"id":"1","visibility":"public","url":"https://x.test/@a/1","content":"duplicado","account":{"acct":"a"}}
                ]}""";
        List<MastodonPost> posts = service().searchPublicStatuses("open data", 20);

        assertEquals(1, posts.size());
        assertEquals("1", posts.get(0).id());
        assertEquals("Olá & mundo", posts.get(0).text());
        assertEquals("a", posts.get(0).accountAcct());
        assertEquals("Bearer " + TOKEN, authHeaders.get(0));
        assertTrue(queries.get(0).contains("q=open+data"));
        assertTrue(queries.get(0).contains("type=statuses"));
    }

    @Test
    void errorMessagesNeverContainToken() {
        status = 401;
        MastodonException error = assertThrows(MastodonException.class,
                () -> service().searchPublicStatuses("termo", 5));
        assertFalse(error.getMessage().contains(TOKEN));
        assertTrue(error.getMessage().contains("401"));
    }

    @Test
    void rateLimitIsReported() {
        status = 429;
        MastodonException error = assertThrows(MastodonException.class,
                () -> service().searchPublicStatuses("termo", 5));
        assertTrue(error.getMessage().contains("429"));
    }

    @Test
    void redirectIsNotFollowed() {
        status = 302;
        location = "http://127.0.0.1:1/outro";
        assertThrows(MastodonException.class, () -> service().searchPublicStatuses("termo", 5));
        assertEquals(1, authHeaders.size());
    }

    @Test
    void malformedPayloadIsRejected() {
        body = "[]";
        assertThrows(MastodonException.class, () -> service().searchPublicStatuses("termo", 5));
    }

    @Test
    void invalidInputIsRejectedBeforeNetwork() {
        MastodonSearchService service = service();
        assertThrows(IllegalArgumentException.class, () -> service.searchPublicStatuses("   ", 5));
        assertThrows(IllegalArgumentException.class, () -> service.searchPublicStatuses("ok", 0));
        assertThrows(IllegalArgumentException.class, () -> service.searchPublicStatuses("ok", 41));
        assertTrue(authHeaders.isEmpty());
    }

    @Test
    void configRejectsPlainHttpForRemoteHostsAndUserInfo() {
        assertThrows(IllegalArgumentException.class,
                () -> new MastodonConfig(URI.create("http://mastodon.example"), TOKEN));
        assertThrows(IllegalArgumentException.class,
                () -> new MastodonConfig(URI.create("https://user:pass@mastodon.social"), TOKEN));
        assertThrows(IllegalArgumentException.class,
                () -> new MastodonConfig(URI.create("https://mastodon.social"), " "));
    }

    @Test
    void configToStringHidesToken() {
        String text = new MastodonConfig(URI.create("https://mastodon.social"), TOKEN).toString();
        assertFalse(text.contains(TOKEN));
    }
}
