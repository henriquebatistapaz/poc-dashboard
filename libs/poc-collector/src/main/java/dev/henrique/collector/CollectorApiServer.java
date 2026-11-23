package dev.henrique.collector;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.henrique.collector.feed.FeedItem;
import dev.henrique.collector.feed.JdkHttpFeedFetcher;
import dev.henrique.collector.feed.RssFeedParser;
import dev.henrique.collector.service.CollectorScheduler;
import dev.henrique.collector.service.CollectorStateStore;
import dev.henrique.collector.service.ScheduledFeed;
import dev.henrique.collector.service.SecretBox;
import dev.henrique.collector.source.*;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class CollectorApiServer implements AutoCloseable {
    private static final int MAX_REQUEST_BYTES = 8_192;
    private static final String OPENAPI = """
            {"openapi":"3.1.0","info":{"title":"poc-collector API","version":"1.0.0"},
             "paths":{
               "/health":{"get":{"summary":"Health check","responses":{"200":{"description":"Available"}}}},
               "/v1/sources":{"get":{"security":[{"bearerAuth":[]}],"responses":{"200":{"description":"Source capabilities"}}}},
               "/v1/collections":{"post":{"security":[{"bearerAuth":[]}],"summary":"Collect once","responses":{"200":{"description":"Collected items"}}}},
               "/v1/items":{"get":{"security":[{"bearerAuth":[]}],"responses":{"200":{"description":"Stored items"}}}},
               "/v1/feeds":{"get":{"security":[{"bearerAuth":[]}],"responses":{"200":{"description":"Scheduled feeds"}}},
                 "post":{"security":[{"bearerAuth":[]}],"summary":"Schedule a feed","responses":{"201":{"description":"Feed scheduled"}}}},
               "/v1/feeds/{id}":{"delete":{"security":[{"bearerAuth":[]}],"responses":{"204":{"description":"Feed removed"}}}}
             },"components":{"securitySchemes":{"bearerAuth":{"type":"http","scheme":"bearer"}}}}
            """;

    private final HttpServer server;
    private final ExecutorService executor;
    private final CollectorService collector;
    private final CollectorStateStore store;
    private final SecretBox secretBox;
    private final byte[] apiKey;
    private final long retentionDays;
    private final ObjectMapper mapper = new ObjectMapper();

    public CollectorApiServer(InetSocketAddress address, CollectorService collector,
                              CollectorStateStore store, SecretBox secretBox,
                              String apiKey, long retentionDays) throws IOException {
        if (apiKey == null || apiKey.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalArgumentException("COLLECTOR_API_KEY must contain at least 32 bytes.");
        }
        if (retentionDays < 1 || retentionDays > 3_650) {
            throw new IllegalArgumentException("COLLECTOR_RETENTION_DAYS must be between 1 and 3650.");
        }
        this.server = HttpServer.create(address, 0);
        this.collector = collector;
        this.store = store;
        this.secretBox = secretBox;
        this.apiKey = apiKey.getBytes(StandardCharsets.UTF_8);
        this.retentionDays = retentionDays;
        this.executor = Executors.newFixedThreadPool(8);
        server.setExecutor(executor);
        server.createContext("/", this::handle);
    }

    public void start() {
        server.start();
    }

    public InetSocketAddress address() {
        return server.getAddress();
    }

    @Override
    public void close() {
        server.stop(1);
        executor.shutdownNow();
    }

    private void handle(HttpExchange exchange) throws IOException {
        try {
            String method = exchange.getRequestMethod();
            String path = exchange.getRequestURI().getPath();
            if ("GET".equals(method) && "/health".equals(path)) {
                send(exchange, 200, "{\"status\":\"UP\"}");
                return;
            }
            if ("GET".equals(method) && "/openapi.json".equals(path)) {
                send(exchange, 200, OPENAPI);
                return;
            }
            if (!authorized(exchange)) {
                send(exchange, 401, "{\"error\":\"unauthorized\"}");
                return;
            }
            if ("GET".equals(method) && "/v1/sources".equals(path)) {
                send(exchange, 200, sourceJson(collector.sources()));
            } else if ("POST".equals(method) && "/v1/collections".equals(path)) {
                collectOnce(exchange);
            } else if ("GET".equals(method) && "/v1/items".equals(path)) {
                listItems(exchange);
            } else if ("GET".equals(method) && "/v1/feeds".equals(path)) {
                listFeeds(exchange);
            } else if ("POST".equals(method) && "/v1/feeds".equals(path)) {
                createFeed(exchange);
            } else if ("DELETE".equals(method) && path.startsWith("/v1/feeds/")) {
                deleteFeed(exchange, path.substring("/v1/feeds/".length()));
            } else {
                send(exchange, 404, "{\"error\":\"not_found\"}");
            }
        } catch (SourceAccessException e) {
            send(exchange, 422, "{\"error\":\"source_unavailable\"}");
        } catch (IllegalArgumentException e) {
            send(exchange, 400, "{\"error\":\"invalid_request\"}");
        } catch (RuntimeException e) {
            send(exchange, 500, "{\"error\":\"internal_error\"}");
        } finally {
            exchange.close();
        }
    }

    private void collectOnce(HttpExchange exchange) throws IOException {
        JsonNode body = readBody(exchange);
        CollectorSource source = parseSource(requiredText(body, "source"));
        String input = requiredText(body, "input");
        List<FeedItem> items = collector.collect(source, input);
        store.addItems(items);
        store.deleteExpired(Instant.now().minusSeconds(retentionDays * 86_400));
        send(exchange, 200, "{\"count\":" + items.size() + ",\"items\":" + FeedItemJson.encode(items) + "}");
    }

    private void listItems(HttpExchange exchange) throws IOException {
        var query = query(exchange.getRequestURI().getRawQuery());
        int limit = parseLimit(query.getOrDefault("limit", "100"));
        CollectorSource source = query.containsKey("source") ? parseSource(query.get("source")) : null;
        send(exchange, 200, FeedItemJson.encode(store.items(source, limit)));
    }

    private void listFeeds(HttpExchange exchange) throws IOException {
        StringBuilder json = new StringBuilder("[");
        List<ScheduledFeed> feeds = store.feeds();
        for (int i = 0; i < feeds.size(); i++) {
            if (i > 0) json.append(',');
            ScheduledFeed feed = feeds.get(i);
            json.append("{\"id\":\"").append(feed.id()).append("\",\"source\":\"")
                    .append(feed.source()).append("\",\"intervalSeconds\":").append(feed.intervalSeconds())
                    .append(",\"nextRunAt\":\"").append(feed.nextRunAt())
                    .append("\",\"enabled\":").append(feed.enabled())
                    .append(",\"lastStatus\":\"").append(feed.lastStatus()).append("\"}");
        }
        send(exchange, 200, json.append(']').toString());
    }

    private void createFeed(HttpExchange exchange) throws IOException {
        JsonNode body = readBody(exchange);
        CollectorSource source = parseSource(requiredText(body, "source"));
        String input = requiredText(body, "input");
        long interval = body.path("intervalSeconds").asLong(3_600);
        if (interval < 300 || interval > 86_400) {
            throw new IllegalArgumentException("intervalSeconds must be from 300 to 86400.");
        }
        SourceCapability capability = collector.sources().stream()
                .filter(candidate -> candidate.source() == source).findFirst().orElseThrow();
        if (capability.status() == CapabilityStatus.UNAVAILABLE
                || capability.status() == CapabilityStatus.UNVERIFIED) {
            throw new SourceAccessException(source + " collector is disabled.");
        }
        ScheduledFeed feed = store.createFeed(source, secretBox.encrypt(input), interval,
                Instant.now().plusSeconds(interval));
        send(exchange, 201, "{\"id\":\"" + feed.id() + "\",\"source\":\"" + feed.source()
                + "\",\"intervalSeconds\":" + feed.intervalSeconds() + "}");
    }

    private void deleteFeed(HttpExchange exchange, String id) throws IOException {
        if (id.isBlank() || id.contains("/")) throw new IllegalArgumentException("Feed ID is invalid.");
        if (store.deleteFeed(id)) send(exchange, 204, "");
        else send(exchange, 404, "{\"error\":\"not_found\"}");
    }

    private JsonNode readBody(HttpExchange exchange) throws IOException {
        byte[] bytes = exchange.getRequestBody().readNBytes(MAX_REQUEST_BYTES + 1);
        if (bytes.length > MAX_REQUEST_BYTES) throw new IllegalArgumentException("Request body is too large.");
        JsonNode body;
        try {
            body = mapper.readTree(bytes);
        } catch (IOException e) {
            throw new IllegalArgumentException("Request body is not valid JSON.");
        }
        if (body == null || !body.isObject()) throw new IllegalArgumentException("JSON object required.");
        return body;
    }

    private static String requiredText(JsonNode body, String field) {
        JsonNode node = body.get(field);
        if (node == null || !node.isTextual() || node.asText().isBlank()) {
            throw new IllegalArgumentException(field + " is required.");
        }
        return node.asText();
    }

    private static CollectorSource parseSource(String value) {
        try {
            return CollectorSource.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Source is invalid.");
        }
    }

    private static int parseLimit(String value) {
        try {
            int limit = Integer.parseInt(value);
            if (limit < 1 || limit > 500) throw new NumberFormatException();
            return limit;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("limit must be from 1 to 500.");
        }
    }

    private static java.util.Map<String, String> query(String rawQuery) {
        java.util.Map<String, String> result = new java.util.HashMap<>();
        if (rawQuery == null || rawQuery.isBlank()) return result;
        for (String part : rawQuery.split("&")) {
            String[] pair = part.split("=", 2);
            result.put(URLDecoder.decode(pair[0], StandardCharsets.UTF_8),
                    pair.length > 1 ? URLDecoder.decode(pair[1], StandardCharsets.UTF_8) : "");
        }
        return result;
    }

    private boolean authorized(HttpExchange exchange) {
        String header = exchange.getRequestHeaders().getFirst("Authorization");
        if (header == null || !header.startsWith("Bearer ")) return false;
        byte[] supplied = header.substring("Bearer ".length()).getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(apiKey, supplied);
    }

    private static String sourceJson(List<SourceCapability> sources) {
        StringBuilder json = new StringBuilder("[");
        for (int i = 0; i < sources.size(); i++) {
            if (i > 0) json.append(',');
            SourceCapability capability = sources.get(i);
            json.append("{\"source\":\"").append(capability.source()).append("\",\"status\":\"")
                    .append(capability.status()).append("\",\"reason\":\"")
                    .append(escape(capability.reason())).append("\"}");
        }
        return json.append(']').toString();
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r");
    }

    private static void send(HttpExchange exchange, int status, String json) throws IOException {
        byte[] body = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        if (status == 204) {
            exchange.sendResponseHeaders(status, -1);
        } else {
            exchange.sendResponseHeaders(status, body.length);
            exchange.getResponseBody().write(body);
        }
    }

    public static void main(String[] args) throws Exception {
        var environment = System.getenv();
        var fetcher = new JdkHttpFeedFetcher();
        var parser = new RssFeedParser();
        var registry = new SourceRegistry();
        var collector = new CollectorService(registry,
                new GoogleNewsCollector(fetcher, parser),
                new WordPressFeedCollector(fetcher, parser));
        SecretBox secretBox = SecretBox.fromEnvironment(environment.get("COLLECTOR_SECRET_KEY"));
        String apiKey = environment.get("COLLECTOR_API_KEY");
        long retentionDays = parseLong(environment.getOrDefault("COLLECTOR_RETENTION_DAYS", "30"), "COLLECTOR_RETENTION_DAYS");
        int port = (int) parseLong(environment.getOrDefault("COLLECTOR_PORT", "8090"), "COLLECTOR_PORT");
        String bind = environment.getOrDefault("COLLECTOR_BIND", "127.0.0.1");
        InetAddress address = InetAddress.getByName(bind);
        Path dataFile = Path.of(environment.getOrDefault("COLLECTOR_DATA_FILE", "collector-data.json"));
        var store = new CollectorStateStore(dataFile);
        var scheduler = new CollectorScheduler(collector, store, secretBox, retentionDays);
        var api = new CollectorApiServer(new InetSocketAddress(address, port), collector, store,
                secretBox, apiKey, retentionDays);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            scheduler.close();
            api.close();
        }));
        store.deleteExpired(Instant.now().minusSeconds(retentionDays * 86_400));
        scheduler.start();
        api.start();
    }

    private static long parseLong(String value, String name) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(name + " must be an integer.");
        }
    }
}
