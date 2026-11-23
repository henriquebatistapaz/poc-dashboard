package dev.henrique.collectoroauth;

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

class MastodonOAuthClientTest {
    private static final String REDIRECT = "http://127.0.0.1:8765/callback";
    private HttpServer server;
    private final List<String> bodies = new CopyOnWriteArrayList<>();
    private final List<String> paths = new CopyOnWriteArrayList<>();
    private int status = 200;
    private String responseBody = "{}";

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            paths.add(exchange.getRequestURI().getPath());
            bodies.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] bytes = responseBody.getBytes(StandardCharsets.UTF_8);
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

    private MastodonOAuthClient client() {
        URI instance = URI.create("http://127.0.0.1:" + server.getAddress().getPort());
        return new MastodonOAuthClient(new MastodonConfig(instance, "nao-usado-aqui"));
    }

    @Test
    void registersAppAndReturnsCredentials() {
        responseBody = "{\"client_id\":\"cid\",\"client_secret\":\"csec\"}";
        MastodonOAuthClient.OAuthApp app = client().registerApp("poc", REDIRECT);

        assertEquals("cid", app.clientId());
        assertEquals("csec", app.clientSecret());
        assertEquals("/api/v1/apps", paths.get(0));
        assertTrue(bodies.get(0).contains("scopes=read+write+follow+push"));
        assertFalse(app.toString().contains("csec"));
    }

    @Test
    void authorizationUrlHasStateAndScope() {
        MastodonOAuthClient.OAuthApp app = new MastodonOAuthClient.OAuthApp("cid", "csec", REDIRECT);
        MastodonOAuthClient.AuthorizationRequest request = client().authorizationRequest(app);

        String url = request.url().toString();
        assertTrue(url.contains("/oauth/authorize?"));
        assertTrue(url.contains("client_id=cid"));
        assertTrue(url.contains("response_type=code"));
        assertTrue(url.contains("scope=read+write+follow+push"));
        assertTrue(url.contains("state=" + request.state()));
        assertTrue(request.state().length() >= 32);
    }

    @Test
    void exchangesCodeForAccessToken() {
        responseBody = "{\"access_token\":\"tok-123\",\"token_type\":\"Bearer\"}";
        MastodonOAuthClient.OAuthApp app = new MastodonOAuthClient.OAuthApp("cid", "csec", REDIRECT);
        String token = client().exchangeCode(app, "codigo-abc");

        assertEquals("tok-123", token);
        assertEquals("/oauth/token", paths.get(0));
        assertTrue(bodies.get(0).contains("grant_type=authorization_code"));
        assertTrue(bodies.get(0).contains("code=codigo-abc"));
    }

    @Test
    void oauthErrorDoesNotLeakSecrets() {
        status = 401;
        responseBody = "{\"error\":\"invalid_client\"}";
        MastodonOAuthClient.OAuthApp app = new MastodonOAuthClient.OAuthApp("cid", "csec-secreto", REDIRECT);
        MastodonException error = assertThrows(MastodonException.class,
                () -> client().exchangeCode(app, "codigo"));
        assertFalse(error.getMessage().contains("csec-secreto"));
        assertTrue(error.getMessage().contains("401"));
    }

    @Test
    void rejectsNonLoopbackPlainHttpRedirect() {
        assertThrows(IllegalArgumentException.class,
                () -> client().registerApp("poc", "http://exemplo.com/callback"));
    }
}
