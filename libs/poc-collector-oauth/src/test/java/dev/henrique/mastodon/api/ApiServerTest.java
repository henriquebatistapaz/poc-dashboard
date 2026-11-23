package dev.henrique.mastodon.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ApiServerTest {
    private static final String FRONTEND = "http://localhost:5173";
    private static final String GOOD_TOKEN = "Bearer tok-abc";
    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();
    private HttpServer fakeMastodon;
    private ApiServer api;
    private String instance;
    private String apiBase;

    @BeforeEach
    void start() throws IOException {
        fakeMastodon = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        fakeMastodon.createContext("/", this::fakeMastodon);
        fakeMastodon.start();
        instance = "http://127.0.0.1:" + fakeMastodon.getAddress().getPort();

        ApiConfig config = new ApiConfig(0, FRONTEND + "/", "http://127.0.0.1:0/api/oauth/callback", false,
                Duration.ofHours(1));
        api = new ApiServer(config, new MastodonBackend(new SessionStore(Duration.ofHours(1)),
                config.redirectUri()));
        api.start();
        apiBase = "http://127.0.0.1:" + api.port();
    }

    @AfterEach
    void stop() {
        api.close();
        fakeMastodon.stop(0);
    }

    /** Mastodon falso: registro, token, busca, publicar, curtir e apagar. */
    private void fakeMastodon(HttpExchange ex) throws IOException {
        String key = ex.getRequestMethod() + " " + ex.getRequestURI().getPath();
        String auth = ex.getRequestHeaders().getFirst("Authorization");
        String json;
        int status = 200;
        switch (key) {
            case "POST /api/v1/apps" -> json = "{\"client_id\":\"cid\",\"client_secret\":\"csec\"}";
            case "POST /oauth/token" -> json = "{\"access_token\":\"tok-abc\"}";
            case "GET /api/v2/search" -> {
                if (!GOOD_TOKEN.equals(auth)) { status = 401; json = "{}"; } else {
                    json = "{\"statuses\":["
                            + "{\"id\":\"1\",\"visibility\":\"public\",\"url\":\"https://x.test/1\","
                            + "\"content\":\"<p>open data</p>\",\"account\":{\"acct\":\"ana\"}},"
                            + "{\"id\":\"2\",\"visibility\":\"private\",\"url\":\"https://x.test/2\","
                            + "\"content\":\"privado\",\"account\":{\"acct\":\"bob\"}}]}";
                }
            }
            case "POST /api/v1/statuses" -> {
                if (!GOOD_TOKEN.equals(auth)) { status = 401; json = "{}"; } else {
                    json = "{\"id\":\"7\",\"url\":\"https://x.test/7\",\"content\":\"<p>oi</p>\","
                            + "\"account\":{\"acct\":\"me\"}}";
                }
            }
            case "POST /api/v1/statuses/7/favourite" -> json = "{\"id\":\"7\",\"favourited\":true}";
            case "DELETE /api/v1/statuses/7" -> json = "{\"id\":\"7\"}";
            default -> { status = 404; json = "{}"; }
        }
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        ex.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = ex.getResponseBody()) {
            out.write(bytes);
        }
    }

    private HttpResponse<String> call(String method, String path, String cookie, String csrf, String body)
            throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(apiBase + path))
                .header("Origin", FRONTEND);
        if (cookie != null) b.header("Cookie", "poc_sid=" + cookie);
        if (csrf != null) b.header("X-Requested-With", csrf);
        if (body != null) b.header("Content-Type", "application/json");
        HttpRequest.BodyPublisher publisher = body == null
                ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(body);
        b.method(method, publisher);
        return http.send(b.build(), HttpResponse.BodyHandlers.ofString());
    }

    private String loginAndGetSession() throws Exception {
        HttpResponse<String> login = call("GET", "/api/oauth/login?instance=" + instance, null, null, null);
        assertEquals(200, login.statusCode());
        String authUrl = mapper.readTree(login.body()).path("authorizationUrl").asText();
        assertTrue(authUrl.startsWith(instance + "/oauth/authorize?"));
        Matcher state = Pattern.compile("state=([^&]+)").matcher(authUrl);
        assertTrue(state.find());

        HttpResponse<String> callback = call("GET",
                "/api/oauth/callback?code=abc&state=" + state.group(1), null, null, null);
        assertEquals(302, callback.statusCode());
        assertEquals(FRONTEND + "/", callback.headers().firstValue("Location").orElseThrow());
        String setCookie = callback.headers().firstValue("Set-Cookie").orElseThrow();
        assertTrue(setCookie.contains("HttpOnly"));
        assertTrue(setCookie.contains("SameSite=Lax"));
        return setCookie.substring("poc_sid=".length(), setCookie.indexOf(';'));
    }

    @Test
    void fullFlowLoginSearchWriteAndLogout() throws Exception {
        assertEquals(200, call("GET", "/api/health", null, null, null).statusCode());
        assertFalse(mapper.readTree(call("GET", "/api/session", null, null, null).body())
                .path("authenticated").asBoolean());

        String sid = loginAndGetSession();

        JsonNode session = mapper.readTree(call("GET", "/api/session", sid, null, null).body());
        assertTrue(session.path("authenticated").asBoolean());
        assertEquals(instance, session.path("instance").asText());

        JsonNode search = mapper.readTree(call("GET", "/api/search?q=open+data", sid, null, null).body());
        assertEquals(1, search.path("posts").size());
        assertEquals("ana", search.path("posts").get(0).path("account").asText());
        assertEquals("open data", search.path("posts").get(0).path("text").asText());

        HttpResponse<String> created = call("POST", "/api/statuses", sid, "1", "{\"text\":\"oi\"}");
        assertEquals(201, created.statusCode());
        assertEquals("7", mapper.readTree(created.body()).path("id").asText());

        JsonNode fav = mapper.readTree(call("POST", "/api/statuses/7/favourite", sid, "1", null).body());
        assertTrue(fav.path("favourited").asBoolean());

        assertEquals(204, call("DELETE", "/api/statuses/7", sid, "1", null).statusCode());

        assertEquals(204, call("POST", "/api/session/logout", sid, "1", null).statusCode());
        assertEquals(401, call("GET", "/api/search?q=x", sid, null, null).statusCode());
    }

    @Test
    void writesWithoutCsrfHeaderAreForbidden() throws Exception {
        String sid = loginAndGetSession();
        assertEquals(403, call("POST", "/api/statuses", sid, null, "{\"text\":\"oi\"}").statusCode());
        assertEquals(403, call("DELETE", "/api/statuses/7", sid, null, null).statusCode());
    }

    @Test
    void searchWithoutSessionIsUnauthorized() throws Exception {
        assertEquals(401, call("GET", "/api/search?q=x", null, null, null).statusCode());
    }

    @Test
    void invalidCallbackStateIsRejected() throws Exception {
        HttpResponse<String> response = call("GET", "/api/oauth/callback?code=abc&state=falso", null, null, null);
        assertEquals(400, response.statusCode());
        assertTrue(mapper.readTree(response.body()).path("error").asText().contains("expirado"));
    }

    @Test
    void corsAllowsOnlyTheFrontendOrigin() throws Exception {
        HttpResponse<String> response = call("GET", "/api/health", null, null, null);
        assertEquals(FRONTEND, response.headers().firstValue("Access-Control-Allow-Origin").orElseThrow());
        assertEquals("true", response.headers().firstValue("Access-Control-Allow-Credentials").orElseThrow());

        HttpRequest foreign = HttpRequest.newBuilder(URI.create(apiBase + "/api/health"))
                .header("Origin", "https://evil.example").GET().build();
        HttpResponse<String> other = http.send(foreign, HttpResponse.BodyHandlers.ofString());
        assertTrue(other.headers().firstValue("Access-Control-Allow-Origin").isEmpty());
    }

    @Test
    void errorsDoNotLeakTokens() throws Exception {
        String sid = loginAndGetSession();
        HttpResponse<String> response = call("POST", "/api/statuses", sid, "1", "{\"text\":\"\"}");
        assertEquals(400, response.statusCode());
        assertFalse(response.body().contains("tok-abc"));
    }
}
