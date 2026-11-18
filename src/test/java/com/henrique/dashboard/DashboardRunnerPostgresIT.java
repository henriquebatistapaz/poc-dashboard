package com.henrique.dashboard;

import com.example.collectorrunner.integration.*;
import com.example.rsscollector.application.RssFeedLinkValidator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.henrique.collector.feed.RssFeedParser;
import com.henrique.collector.source.*;
import com.henrique.dashboard.repository.CollectedItemRepository;
import com.henrique.dashboard.repository.ItemRemovalRepository;
import com.henrique.dashboard.integration.RemovalAwareCollectionGateway;
import java.net.URI;
import java.net.InetSocketAddress;
import java.io.IOException;
import com.sun.net.httpserver.HttpServer;
import java.nio.charset.StandardCharsets;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(DashboardRunnerPostgresIT.FixtureConfiguration.class)
class DashboardRunnerPostgresIT {
    @Container static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18-alpine");
    private static final HttpClient http = HttpClient.newHttpClient();
    @LocalServerPort int port;
    @Autowired ObjectMapper json;
    @Autowired CollectedItemRepository items;
    @Autowired ItemRemovalRepository removals;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("dashboard.api-key", () -> "integration-reader");
        registry.add("dashboard.admin-email", () -> "admin@example.org");
        registry.add("dashboard.admin-password-hash", () -> new BCryptPasswordEncoder(4).encode("test-password"));
        registry.add("dashboard.jwt-secret", () -> "integration-test-secret-at-least-32-bytes");
    }

    @BeforeEach
    void clean() {
        removals.deleteAll();
        items.deleteAll();
    }

    private ResponseEntity<String> exchange(String path, HttpMethod method, HttpEntity<?> entity) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port).resolve(path));
        entity.getHeaders().forEach((name, values) -> values.forEach(value -> request.header(name, value)));
        if (entity.getBody() == null) {
            request.method(method.name(), HttpRequest.BodyPublishers.noBody());
        } else {
            request.header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
            request.method(method.name(), HttpRequest.BodyPublishers.ofString(json.writeValueAsString(entity.getBody())));
        }
        var response = http.send(request.build(), HttpResponse.BodyHandlers.ofString());
        var headers = new HttpHeaders();
        response.headers().map().forEach(headers::put);
        return new ResponseEntity<>(response.body(), headers, HttpStatusCode.valueOf(response.statusCode()));
    }

    private String login() throws Exception {
        var response = exchange("/v1/auth/login", HttpMethod.POST,
                new HttpEntity<>(Map.of("email", "admin@example.org", "password", "test-password")));
        assertEquals(HttpStatus.OK, response.getStatusCode());
        return json.readTree(response.getBody()).path("accessToken").asText();
    }

    private ResponseEntity<String> collect(CollectionSource source, String input, String token) throws Exception {
        var headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return exchange("/v1/collections", HttpMethod.POST,
                new HttpEntity<>(Map.of("source", source.name(), "input", input), headers));
    }

    private JsonNode awaitRun(ResponseEntity<String> accepted, String token) throws Exception {
        String location = accepted.getHeaders().getFirst(HttpHeaders.LOCATION);
        assertNotNull(location, "202 response must carry a Location header");
        JsonNode run = null;
        for (int attempt = 0; attempt < 200; attempt++) {
            var headers = new HttpHeaders();
            headers.setBearerAuth(token);
            run = json.readTree(exchange(URI.create(location).getPath(), HttpMethod.GET,
                    new HttpEntity<>(headers)).getBody());
            String status = run.path("status").asText();
            if ("SUCCEEDED".equals(status) || "FAILED".equals(status)) {
                return run;
            }
            Thread.sleep(50);
        }
        fail("collection run did not finish: " + run);
        return run;
    }

    @ParameterizedTest
    @EnumSource(value = CollectionSource.class, names = {"ALPHA", "BETA", "RSS", "GOOGLE_NEWS", "WORDPRESS", "MASTODON", "LEMMY", "DEV_TO", "HACKER_NEWS", "STACK_EXCHANGE", "GITHUB", "TABNEWS", "WIKIPEDIA", "GITLAB", "LOBSTERS"})
    void collectsParsesPersistsDeduplicatesAndQueriesOverHttp(CollectionSource source) throws Exception {
        String input = switch (source) {
            case ALPHA -> """
                {"posts":[{"id":"fixture-1","author":{"handle":"demo"},"text":"Integration title",
                "created_at":"2026-11-06T00:00:00Z","url":"https://example.org/item"}]}
                """;
            case BETA -> """
                {"items":[{"postId":"fixture-1","authorName":"demo","body":"Integration title",
                "publishedAt":"2026-11-06T00:00:00Z","link":"https://example.org/item"}]}
                """;
            case RSS -> "https://example.org/feed";
            case TABNEWS -> "relevant";
            case LOBSTERS -> "hottest";
            default -> "java";
        };
        String token = login();
        var first = collect(source, input, token);
        assertEquals(HttpStatus.ACCEPTED, first.getStatusCode(), source + " input=" + input + ": " + first.getBody());
        JsonNode firstRun = awaitRun(first, token);
        assertEquals("SUCCEEDED", firstRun.path("status").asText(), firstRun.toString());
        assertEquals(1, firstRun.path("inserted").asInt());
        var repeated = collect(source, input, token);
        assertEquals(HttpStatus.ACCEPTED, repeated.getStatusCode());
        JsonNode repeatedRun = awaitRun(repeated, token);
        assertEquals("SUCCEEDED", repeatedRun.path("status").asText(), repeatedRun.toString());
        assertEquals(0, repeatedRun.path("inserted").asInt());
        assertEquals(1, repeatedRun.path("duplicates").asInt());
        assertEquals(1, items.count());

        var headers = new HttpHeaders();
        headers.set("X-API-Key", "integration-reader");
        String storedSource = source.name().toLowerCase(java.util.Locale.ROOT).replace('_', '-');
        var listed = exchange("/v1/items?source=" + storedSource + "&size=1", HttpMethod.GET, new HttpEntity<>(headers));
        assertEquals(HttpStatus.OK, listed.getStatusCode());
        JsonNode body = json.readTree(listed.getBody());
        assertEquals(1, body.path("total").asInt());
        JsonNode item = body.path("items").get(0);
        String expectedTitle = switch (source) {
            case GITHUB -> "org/repo — Integration title";
            case WIKIPEDIA -> "Wikipedia fixture";
            case GITLAB -> "org/project";
            case LOBSTERS -> "Lobsters fixture";
            default -> "Integration title";
        };
        String expectedLink = switch (source) {
            case MASTODON -> "https://mastodon.social/@demo/1";
            case LEMMY -> "https://lemmy.world/post/1";
            case DEV_TO -> "https://dev.to/demo/1";
            case HACKER_NEWS -> "https://news.ycombinator.com/item?id=3";
            case STACK_EXCHANGE -> "https://stackoverflow.com/q/4";
            case GITHUB -> "https://github.com/org/repo";
            case TABNEWS -> "https://www.tabnews.com.br/demo/post";
            case WIKIPEDIA -> "https://en.wikipedia.org/?curid=42";
            case GITLAB -> "https://gitlab.com/org/project";
            case LOBSTERS -> "https://example.org/lobsters";
            default -> "https://example.org/item";
        };
        assertEquals(expectedTitle, item.path("title").asText());
        assertEquals(expectedLink, item.path("link").asText());
        var enumListed = exchange("/v1/items?source=" + source.name() + "&size=1", HttpMethod.GET,
                new HttpEntity<>(headers));
        assertEquals(1, json.readTree(enumListed.getBody()).path("total").asInt());
        assertFalse(item.path("collectedAt").asText().isBlank());
        var detail = exchange("/v1/items/" + item.path("id").asLong(), HttpMethod.GET, new HttpEntity<>(headers));
        assertEquals(HttpStatus.OK, detail.getStatusCode());
        assertEquals(item.path("externalId"), json.readTree(detail.getBody()).path("externalId"));

        var adminHeaders = new HttpHeaders();
        adminHeaders.setBearerAuth(token);
        adminHeaders.setContentType(MediaType.APPLICATION_JSON);
        var deleted = exchange("/v1/items/" + item.path("id").asLong(), HttpMethod.DELETE,
                new HttpEntity<>(Map.of("reason", "integration test"), adminHeaders));
        assertEquals(HttpStatus.NO_CONTENT, deleted.getStatusCode());
        assertEquals(0, items.count());
        assertEquals(1, removals.count());

        JsonNode afterRemoval = awaitRun(collect(source, input, token), token);
        assertEquals("SUCCEEDED", afterRemoval.path("status").asText(), afterRemoval.toString());
        assertEquals(0, afterRemoval.path("inserted").asInt());
        assertEquals(0, afterRemoval.path("duplicates").asInt());
        assertEquals(0, items.count());
    }

    @Test
    void disabledSourcesNeverPersistAndPrivateRssUrlsAreRejected() throws Exception {
        String token = login();
        var issuu = awaitRun(collect(CollectionSource.ISSUU, "demo", token), token);
        assertEquals("FAILED", issuu.path("status").asText());
        assertEquals("Source unavailable; check approval and runtime configuration", issuu.path("errorMessage").asText());
        var privateRss = awaitRun(collect(CollectionSource.RSS, "http://127.0.0.1/private", token), token);
        assertEquals("FAILED", privateRss.path("status").asText());
        assertEquals("Invalid source input or unsupported item fields", privateRss.path("errorMessage").asText());
        assertEquals(0, items.count());
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FixtureConfiguration {
        @Bean(destroyMethod = "close")
        FixtureServer restFixtureServer() {
            try {
                var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
                Map<String, String> responses = Map.of(
                        "/mastodon", "[{\"id\":\"rest-1\",\"content\":\"<p>Integration title</p>\",\"url\":\"https://mastodon.social/@demo/1\"}]",
                        "/lemmy", "{\"posts\":[{\"post\":{\"id\":1,\"name\":\"Integration title\",\"url\":\"https://lemmy.world/post/1\"}}]}",
                        "/devto", "[{\"id\":2,\"title\":\"Integration title\",\"url\":\"https://dev.to/demo/1\"}]",
                        "/hn", "{\"hits\":[{\"objectID\":\"3\",\"title\":\"Integration title\",\"url\":\"https://news.ycombinator.com/item?id=3\"}]}",
                        "/stack", "{\"items\":[{\"question_id\":4,\"title\":\"Integration title\",\"link\":\"https://stackoverflow.com/q/4\"}]}",
                        "/github", "{\"items\":[{\"id\":5,\"full_name\":\"org/repo\",\"description\":\"Integration title\",\"html_url\":\"https://github.com/org/repo\"}]}",
                        "/tabnews", "[{\"id\":\"6\",\"owner_username\":\"demo\",\"slug\":\"post\",\"title\":\"Integration title\"}]",
                        "/w/api.php", "{\"query\":{\"search\":[{\"pageid\":42,\"title\":\"Wikipedia fixture\",\"timestamp\":\"2026-11-18T12:00:00Z\"}]}}",
                        "/api/v4/projects", "[{\"id\":7,\"name_with_namespace\":\"org/project\",\"web_url\":\"https://gitlab.com/org/project\",\"last_activity_at\":\"2026-11-18T12:00:00Z\"}]",
                        "/hottest.json", "[{\"short_id\":\"abc123\",\"title\":\"Lobsters fixture\",\"url\":\"https://example.org/lobsters\",\"created_at\":\"2026-11-18T12:00:00Z\"}]");
                server.createContext("/", exchange -> {
                    String path = exchange.getRequestURI().getPath();
                    String key = path.startsWith("/mastodon/") ? "/mastodon" : path;
                    byte[] body = responses.getOrDefault(key, "{}").getBytes(StandardCharsets.UTF_8);
                    exchange.getResponseHeaders().set("Content-Type", "application/json");
                    exchange.sendResponseHeaders(200, body.length);
                    exchange.getResponseBody().write(body);
                    exchange.close();
                });
                server.start();
                return new FixtureServer(server);
            } catch (IOException exception) {
                throw new IllegalStateException(exception);
            }
        }

        @Bean @Primary
        CollectionGateway fixtureGateway(FixtureServer server, ItemRemovalRepository removals) {
            String rss = """
                <rss version="2.0"><channel><title>test</title><item><guid>fixture-1</guid>
                <title>Integration title</title><link>https://example.org/item</link>
                <pubDate>Fri, 06 Nov 2026 00:00:00 GMT</pubDate></item></channel></rss>
                """;
            String atom = """
                <feed xmlns="http://www.w3.org/2005/Atom"><id>feed-1</id><title>test</title>
                <updated>2026-11-06T00:00:00Z</updated><entry><id>fixture-1</id>
                <title>Integration title</title><link href="https://example.org/item"/>
                <updated>2026-11-06T00:00:00Z</updated></entry></feed>
                """;
            com.henrique.collector.feed.FeedFetcher fetcher = uri -> rss.getBytes(StandardCharsets.UTF_8);
            var parser = new RssFeedParser();
            var service = new CollectorService(new SourceRegistry(), new GoogleNewsCollector(fetcher, parser),
                    new WordPressFeedCollector(fetcher, parser));
            var urls = new RssFeedLinkValidator();
            // Only upstream fetching/DNS are fixtures: parsers, runner, SQL, Flyway, security and HTTP API are real.
            var existingSources = new DefaultCollectionGateway(service, url -> atom,
                    new com.example.rsscollector.application.RssFeedParser(urls), urls);
            URI base = URI.create("http://127.0.0.1:" + server.server().getAddress().getPort());
            var restSources = DefaultCollectionGateway.fromEnvironment(Map.of(), Map.of(
                    CollectionSource.MASTODON, base.resolve("/mastodon"), CollectionSource.LEMMY, base.resolve("/lemmy"),
                    CollectionSource.DEV_TO, base.resolve("/devto"), CollectionSource.HACKER_NEWS, base.resolve("/hn"),
                    CollectionSource.STACK_EXCHANGE, base.resolve("/stack"), CollectionSource.GITHUB, base.resolve("/github"),
                    CollectionSource.TABNEWS, base.resolve("/tabnews"), CollectionSource.WIKIPEDIA, base.resolve("/w/api.php"),
                    CollectionSource.GITLAB, base.resolve("/api/v4/projects"), CollectionSource.LOBSTERS, base));
            CollectionGateway delegate = (source, input) -> switch (source) {
                case MASTODON, LEMMY, DEV_TO, HACKER_NEWS, STACK_EXCHANGE, GITHUB, TABNEWS,
                        WIKIPEDIA, GITLAB, LOBSTERS -> restSources.collect(source, input);
                default -> existingSources.collect(source, input);
            };
            return new RemovalAwareCollectionGateway(delegate, removals);
        }
    }

    record FixtureServer(HttpServer server) implements AutoCloseable {
        @Override public void close() { server.stop(0); }
    }
}
