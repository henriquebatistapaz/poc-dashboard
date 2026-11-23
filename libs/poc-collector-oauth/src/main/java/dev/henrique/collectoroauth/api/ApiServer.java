package dev.henrique.collectoroauth.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.henrique.collectoroauth.MastodonException;
import dev.henrique.collectoroauth.MastodonPost;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executors;

/**
 * API REST em JSON para um frontend futuro. Escuta só em 127.0.0.1.
 * Sessão por cookie HttpOnly; escritas exigem o cabeçalho X-Requested-With (proteção CSRF).
 */
public final class ApiServer implements AutoCloseable {
    static final String SESSION_COOKIE = "poc_sid";
    static final String CSRF_HEADER = "X-Requested-With";
    private static final int MAX_BODY_BYTES = 16 * 1024;
    private static final int DEFAULT_LIMIT = 20;

    private final ApiConfig config;
    private final MastodonBackend backend;
    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpServer server;

    public ApiServer(ApiConfig config, MastodonBackend backend) throws IOException {
        this.config = config;
        this.backend = backend;
        this.server = HttpServer.create(new InetSocketAddress("127.0.0.1", config.port()), 0);
        server.createContext("/api/", this::handle);
        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
    }

    public void start() {
        server.start();
    }

    public int port() {
        return server.getAddress().getPort();
    }

    @Override public void close() {
        server.stop(0);
    }

    private void handle(HttpExchange ex) throws IOException {
        try {
            addCors(ex);
            String method = ex.getRequestMethod();
            if ("OPTIONS".equals(method)) {
                send(ex, 204, null);
                return;
            }
            route(ex, method, ex.getRequestURI().getPath(), params(ex.getRequestURI().getRawQuery()));
        } catch (ApiException exception) {
            sendError(ex, exception.status(), exception.getMessage());
        } catch (IllegalArgumentException exception) {
            sendError(ex, 400, exception.getMessage());
        } catch (MastodonException exception) {
            sendError(ex, 502, exception.getMessage());
        } catch (RuntimeException exception) {
            sendError(ex, 500, "Erro interno.");
        } finally {
            ex.close();
        }
    }

    private void route(HttpExchange ex, String method, String path, Map<String, String> q) throws IOException {
        if (path.equals("/api/health") && method.equals("GET")) {
            send(ex, 200, mapper.createObjectNode().put("status", "ok"));
            return;
        }
        if (path.equals("/api/oauth/login") && method.equals("GET")) {
            String url = backend.startLogin(required(q, "instance"));
            send(ex, 200, mapper.createObjectNode().put("authorizationUrl", url));
            return;
        }
        if (path.equals("/api/oauth/callback") && method.equals("GET")) {
            callback(ex, q);
            return;
        }
        if (path.equals("/api/session") && method.equals("GET")) {
            session(ex);
            return;
        }
        if (path.equals("/api/session/logout") && method.equals("POST")) {
            requireCsrf(ex);
            backend.logout(sessionCookie(ex));
            ex.getResponseHeaders().add("Set-Cookie", SESSION_COOKIE + "=; Path=/; Max-Age=0; HttpOnly; SameSite=Lax");
            send(ex, 204, null);
            return;
        }
        if (path.equals("/api/search") && method.equals("GET")) {
            search(ex, q);
            return;
        }
        if (path.equals("/api/statuses") && method.equals("POST")) {
            publish(ex);
            return;
        }
        if (path.startsWith("/api/statuses/")) {
            String rest = path.substring("/api/statuses/".length());
            if (rest.endsWith("/favourite") && method.equals("POST")) {
                favourite(ex, rest.substring(0, rest.length() - "/favourite".length()));
                return;
            }
            if (!rest.contains("/") && method.equals("DELETE")) {
                delete(ex, rest);
                return;
            }
        }
        throw new ApiException(404, "Rota não encontrada.");
    }

    private void callback(HttpExchange ex, Map<String, String> q) throws IOException {
        if (q.containsKey("error")) {
            throw new ApiException(400, "Autorização recusada na instância.");
        }
        String sid = backend.finishLogin(required(q, "state"), required(q, "code"));
        ex.getResponseHeaders().add("Set-Cookie", sessionCookieHeader(sid));
        ex.getResponseHeaders().set("Location", config.frontendUrl());
        send(ex, 302, null);
    }

    private void session(HttpExchange ex) throws IOException {
        Optional<String> instance = backend.instanceOf(sessionCookie(ex));
        ObjectNode body = mapper.createObjectNode().put("authenticated", instance.isPresent());
        instance.ifPresent(origin -> body.put("instance", origin));
        send(ex, 200, body);
    }

    private void search(HttpExchange ex, Map<String, String> q) throws IOException {
        String sid = requireSession(ex);
        String query = required(q, "q");
        int limit = q.containsKey("limit") ? parseInt(q.get("limit"), "limit") : DEFAULT_LIMIT;
        List<MastodonPost> posts = backend.search(sid, query, limit);
        ObjectNode body = mapper.createObjectNode();
        ArrayNode items = body.putArray("posts");
        posts.forEach(post -> items.add(postJson(post)));
        send(ex, 200, body);
    }

    private void publish(HttpExchange ex) throws IOException {
        requireCsrf(ex);
        String sid = requireSession(ex);
        JsonNode input = readJson(ex);
        String text = input.path("text").isTextual() ? input.path("text").asText() : null;
        String visibility = input.path("visibility").isTextual() ? input.path("visibility").asText() : null;
        MastodonPost post = backend.publish(sid, text, visibility);
        send(ex, 201, postJson(post));
    }

    private void favourite(HttpExchange ex, String statusId) throws IOException {
        requireCsrf(ex);
        String sid = requireSession(ex);
        boolean favourited = backend.favourite(sid, statusId);
        send(ex, 200, mapper.createObjectNode().put("favourited", favourited));
    }

    private void delete(HttpExchange ex, String statusId) throws IOException {
        requireCsrf(ex);
        String sid = requireSession(ex);
        backend.delete(sid, statusId);
        send(ex, 204, null);
    }

    private ObjectNode postJson(MastodonPost post) {
        ObjectNode node = mapper.createObjectNode();
        node.put("id", post.id());
        node.put("url", post.url());
        node.put("account", post.accountAcct());
        node.put("text", post.text());
        node.put("createdAt", post.createdAt() == null ? null : post.createdAt().toString());
        return node;
    }

    private void addCors(HttpExchange ex) {
        String origin = ex.getRequestHeaders().getFirst("Origin");
        if (origin == null || !origin.equals(config.frontendOrigin())) return;
        Headers h = ex.getResponseHeaders();
        h.set("Access-Control-Allow-Origin", origin);
        h.set("Access-Control-Allow-Credentials", "true");
        h.set("Access-Control-Allow-Headers", "Content-Type, " + CSRF_HEADER);
        h.set("Access-Control-Allow-Methods", "GET, POST, DELETE, OPTIONS");
        h.set("Vary", "Origin");
    }

    private void requireCsrf(HttpExchange ex) {
        String value = ex.getRequestHeaders().getFirst(CSRF_HEADER);
        if (value == null || value.isBlank()) {
            throw new ApiException(403, "Cabeçalho X-Requested-With obrigatório.");
        }
    }

    private String requireSession(HttpExchange ex) {
        String sid = sessionCookie(ex);
        if (sid == null) throw new ApiException(401, "Sessão ausente. Faça login de novo.");
        return sid;
    }

    private JsonNode readJson(HttpExchange ex) throws IOException {
        String type = ex.getRequestHeaders().getFirst("Content-Type");
        if (type == null || !type.toLowerCase().startsWith("application/json")) {
            throw new ApiException(415, "Use Content-Type: application/json.");
        }
        byte[] bytes;
        try (InputStream in = ex.getRequestBody()) {
            bytes = in.readNBytes(MAX_BODY_BYTES + 1);
        }
        if (bytes.length > MAX_BODY_BYTES) throw new ApiException(413, "Corpo da requisição grande demais.");
        JsonNode json = mapper.readTree(bytes);
        if (json == null || !json.isObject()) throw new IllegalArgumentException("Corpo JSON inválido.");
        return json;
    }

    private static String sessionCookie(HttpExchange ex) {
        String header = ex.getRequestHeaders().getFirst("Cookie");
        if (header == null) return null;
        for (String part : header.split(";")) {
            String item = part.trim();
            if (item.startsWith(SESSION_COOKIE + "=")) return item.substring(SESSION_COOKIE.length() + 1);
        }
        return null;
    }

    private String sessionCookieHeader(String sid) {
        return SESSION_COOKIE + "=" + sid + "; Path=/; HttpOnly; SameSite=Lax; Max-Age="
                + config.sessionTtl().toSeconds() + (config.secureCookies() ? "; Secure" : "");
    }

    private void send(HttpExchange ex, int status, JsonNode body) throws IOException {
        if (body == null) {
            ex.sendResponseHeaders(status, -1);
            return;
        }
        byte[] bytes = mapper.writeValueAsBytes(body);
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        ex.getResponseHeaders().set("Cache-Control", "no-store");
        ex.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = ex.getResponseBody()) {
            out.write(bytes);
        }
    }

    private void sendError(HttpExchange ex, int status, String message) throws IOException {
        send(ex, status, mapper.createObjectNode().put("error", message));
    }

    private static String required(Map<String, String> q, String name) {
        String value = q.get(name);
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Parâmetro obrigatório: " + name);
        return value;
    }

    private static int parseInt(String value, String name) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Parâmetro " + name + " precisa ser um número inteiro.");
        }
    }

    static Map<String, String> params(String raw) {
        Map<String, String> result = new HashMap<>();
        if (raw == null || raw.isEmpty()) return result;
        for (String part : raw.split("&")) {
            int eq = part.indexOf('=');
            String key = decode(eq < 0 ? part : part.substring(0, eq));
            String value = eq < 0 ? "" : decode(part.substring(eq + 1));
            result.putIfAbsent(key, value);
        }
        return result;
    }

    private static String decode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }
}
