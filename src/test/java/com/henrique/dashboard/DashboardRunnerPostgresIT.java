package com.henrique.dashboard;

import com.example.collectorrunner.integration.*;
import com.example.rsscollector.application.RssFeedLinkValidator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.henrique.collector.feed.RssFeedParser;
import com.henrique.collector.source.*;
import com.henrique.dashboard.repository.CollectedItemRepository;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
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
@AutoConfigureTestRestTemplate
@Import(DashboardRunnerPostgresIT.FixtureConfiguration.class)
class DashboardRunnerPostgresIT {
    @Container static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18-alpine");
    @Autowired TestRestTemplate http;
    @Autowired ObjectMapper json;
    @Autowired CollectedItemRepository items;

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
    void clean() { items.deleteAll(); }

    private String login() throws Exception {
        var response = http.postForEntity("/v1/auth/login", Map.of("email", "admin@example.org", "password", "test-password"), String.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        return json.readTree(response.getBody()).path("accessToken").asText();
    }

    private ResponseEntity<String> collect(CollectionSource source, String input, String token) {
        var headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return http.exchange("/v1/collections", HttpMethod.POST,
                new HttpEntity<>(Map.of("source", source.name(), "input", input), headers), String.class);
    }

    private JsonNode awaitRun(ResponseEntity<String> accepted, String token) throws Exception {
        String location = accepted.getHeaders().getFirst(HttpHeaders.LOCATION);
        assertNotNull(location, "202 response must carry a Location header");
        JsonNode run = null;
        for (int attempt = 0; attempt < 200; attempt++) {
            var headers = new HttpHeaders();
            headers.setBearerAuth(token);
            run = json.readTree(http.exchange(URI.create(location).getPath(), HttpMethod.GET,
                    new HttpEntity<>(headers), String.class).getBody());
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
    @EnumSource(value = CollectionSource.class, names = {"ALPHA", "BETA", "RSS", "GOOGLE_NEWS", "GOOGLE_ALERTS", "WORDPRESS"})
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
            case GOOGLE_ALERTS -> "https://www.google.com/alerts/feeds/demo/secret";
            default -> "java";
        };
        String token = login();
        var first = collect(source, input, token);
        assertEquals(HttpStatus.ACCEPTED, first.getStatusCode(), first.getBody());
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
        var listed = http.exchange("/v1/items?source=" + storedSource + "&q=integration&size=1", HttpMethod.GET, new HttpEntity<>(headers), String.class);
        assertEquals(HttpStatus.OK, listed.getStatusCode());
        JsonNode body = json.readTree(listed.getBody());
        assertEquals(1, body.path("total").asInt());
        JsonNode item = body.path("items").get(0);
        assertEquals("Integration title", item.path("title").asText());
        assertEquals("https://example.org/item", item.path("link").asText());
        assertFalse(item.path("collectedAt").asText().isBlank());
        var detail = http.exchange("/v1/items/" + item.path("id").asLong(), HttpMethod.GET, new HttpEntity<>(headers), String.class);
        assertEquals(HttpStatus.OK, detail.getStatusCode());
        assertEquals(item.path("externalId"), json.readTree(detail.getBody()).path("externalId"));
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
        @Bean @Primary
        CollectionGateway fixtureGateway() {
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
                    new GoogleAlertsCollector(fetcher, parser, new GoogleAlertsUrlPolicy()),
                    new WordPressFeedCollector(fetcher, parser));
            var urls = new RssFeedLinkValidator();
            // Only upstream fetching/DNS are fixtures: parsers, runner, SQL, Flyway, security and HTTP API are real.
            return new DefaultCollectionGateway(service, url -> atom,
                    new com.example.rsscollector.application.RssFeedParser(urls), urls);
        }
    }
}
