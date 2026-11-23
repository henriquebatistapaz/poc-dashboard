package smoke;

import dev.henrique.collectorrunner.integration.CollectionGateway;
import dev.henrique.collectorrunner.integration.DefaultCollectionGateway;
import dev.henrique.rsscollector.application.FeedDocumentFetcher;
import dev.henrique.rsscollector.application.RssFeedLinkValidator;
import dev.henrique.rsscollector.application.RssFeedParser;
import com.henrique.collector.source.CollectorService;
import com.henrique.collector.source.GoogleNewsCollector;
import com.henrique.collector.source.SourceAccessException;
import com.henrique.collector.source.SourceRegistry;
import com.henrique.collector.source.WordPressFeedCollector;
import dev.henrique.dashboard.DashboardApplication;
import dev.henrique.dashboard.integration.RemovalAwareCollectionGateway;
import dev.henrique.dashboard.repository.ItemRemovalRepository;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import org.springframework.boot.SpringApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;

/**
 * Smoke-only launcher on test classpath. Allows fetching ONLY the explicitly configured local HTTP
 * fixture URL while preserving production SSRF validation for all other addresses and external hosts.
 */
@Configuration(proxyBeanMethods = false)
@Profile("smoke")
public class SmokeApplication {

    public static void main(String[] args) {
        SpringApplication.run(new Class<?>[] { DashboardApplication.class, SmokeApplication.class }, args);
    }

    @Bean
    @Primary
    CollectionGateway smokeCollectionGateway(ItemRemovalRepository removals) {
        String allowedFeedUrl = System.getenv("DASHBOARD_SMOKE_FEED_URL");
        if (allowedFeedUrl == null || allowedFeedUrl.isBlank()) {
            throw new IllegalStateException("DASHBOARD_SMOKE_FEED_URL must be configured for smoke runs");
        }
        String normalizedAllowed = allowedFeedUrl.trim();
        RssFeedLinkValidator smokeValidator = new RssFeedLinkValidator() {
            @Override
            public boolean isValidFeedUrl(String value) {
                return value != null && value.trim().equals(normalizedAllowed);
            }

            @Override
            public boolean resolvesOnlyToPublicAddresses(String value) {
                return value != null && value.trim().equals(normalizedAllowed);
            }
        };

        HttpClient client = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NEVER)
                .connectTimeout(Duration.ofSeconds(5))
                .build();

        FeedDocumentFetcher fixtureOnlyFetcher = feedUrl -> {
            if (feedUrl == null || !feedUrl.trim().equals(normalizedAllowed)) {
                throw new IllegalArgumentException("Blocked non-fixture feed URL in smoke mode");
            }
            HttpRequest request = HttpRequest.newBuilder(URI.create(normalizedAllowed))
                    .timeout(Duration.ofSeconds(5))
                    .GET()
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new IOException("Fixture HTTP status " + response.statusCode());
            }
            return response.body();
        };

        com.henrique.collector.feed.FeedFetcher blockedExternalFetcher = uri -> {
            throw new SourceAccessException("External sources are disabled in smoke mode");
        };
        var legacyParser = new com.henrique.collector.feed.RssFeedParser();
        var collectorService = new CollectorService(
                new SourceRegistry(),
                new GoogleNewsCollector(blockedExternalFetcher, legacyParser),
                new WordPressFeedCollector(blockedExternalFetcher, legacyParser));

        var delegate = new DefaultCollectionGateway(
                collectorService,
                fixtureOnlyFetcher,
                new RssFeedParser(smokeValidator),
                smokeValidator);
        return new RemovalAwareCollectionGateway(delegate, removals);
    }
}
