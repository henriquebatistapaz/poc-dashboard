package com.henrique.dashboard;

import com.example.collectorrunner.integration.*;
import com.example.rsscollector.application.RssFeedLinkValidator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.henrique.collector.feed.RssFeedParser;
import com.henrique.collector.source.*;
import com.henrique.dashboard.repository.CollectedItemRepository;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.client.TestRestTemplate;
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
    @Container static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");
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
            case RSS, WORDPRESS -> "https://example.org/feed";
            case GOOGLE_ALERTS -> "https://www.google.com/alerts/feeds/demo/secret";
            default -> "java";
        };
        String token = login();
        var first = collect(source, input, token);
        assertEquals(HttpStatus.OK, first.getStatusCode(), first.getBody());
        assertEquals(1, json.readTree(first.getBody()).path("inserted").asInt());
        var repeated = collect(source, input, token);
        assertEquals(HttpStatus.OK, repeated.getStatusCode());
        assertEquals(0, json.readTree(repeated.getBody()).path("inserted").asInt());
        assertEquals(1, json.readTree(repeated.getBody()).path("duplicates").asInt());
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
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, collect(CollectionSource.REDDIT, "java", token).getStatusCode());
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, collect(CollectionSource.ISSUU, "demo", token).getStatusCode());
        assertEquals(HttpStatus.BAD_REQUEST, collect(CollectionSource.RSS, "http://127.0.0.1/private", token).getStatusCode());
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
            var redditConfig = RedditOAuthConfig.fromEnvironment(Map.of());
            var redditTransport = new JdkRedditHttpTransport();
            var service = new CollectorService(new SourceRegistry(), new GoogleNewsCollector(fetcher, parser),
                    new GoogleAlertsCollector(fetcher, parser, new GoogleAlertsUrlPolicy()),
                    new WordPressFeedCollector(fetcher, parser, new PublicHttpsFeedUrlPolicy(host ->
                            new InetAddress[]{InetAddress.getByAddress(new byte[]{8,8,8,8})})),
                    new RedditCollector(redditConfig, new RedditOAuthTokenProvider(redditConfig, redditTransport), redditTransport));
            var urls = new RssFeedLinkValidator();
            // Only upstream fetching/DNS are fixtures: parsers, runner, SQL, Flyway, security and HTTP API are real.
            return new DefaultCollectionGateway(service, url -> atom,
                    new com.example.rsscollector.application.RssFeedParser(urls), urls);
        }
    }
}
