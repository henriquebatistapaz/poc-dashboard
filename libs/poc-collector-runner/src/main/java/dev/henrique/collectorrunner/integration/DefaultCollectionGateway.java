package dev.henrique.collectorrunner.integration;

import dev.henrique.rsscollector.application.BoundedFeedHttpFetcher;
import dev.henrique.rsscollector.application.FeedDocumentFetcher;
import dev.henrique.rsscollector.application.RssFeedLinkValidator;
import com.henrique.collector.feed.JdkHttpFeedFetcher;
import com.henrique.collector.source.*;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;

/** Explicit composition avoids scanning the applications/controllers/entities of the library dependencies. */
@RequiredArgsConstructor
public final class DefaultCollectionGateway implements CollectionGateway {
    private final CollectorService collectors;
    private final FeedDocumentFetcher rssFetcher;
    private final dev.henrique.rsscollector.application.RssFeedParser rssParser;
    private final RssFeedLinkValidator rssUrls;

    public static DefaultCollectionGateway fromEnvironment(Map<String, String> environment) {
        return fromEnvironment(environment, Map.of());
    }

    /** Test seam for local HttpServer fixtures; production always uses the providers' fixed HTTPS endpoints. */
    public static DefaultCollectionGateway fromEnvironment(Map<String, String> environment,
                                                            Map<CollectionSource, URI> restEndpoints) {
        var fetcher = new JdkHttpFeedFetcher();
        var parser = new com.henrique.collector.feed.RssFeedParser();
        var service = new CollectorService(new SourceRegistry(),
                new GoogleNewsCollector(fetcher, parser),
                new WordPressFeedCollector(fetcher, parser),
                new MastodonCollector(endpoint(restEndpoints, CollectionSource.MASTODON, "https://mastodon.social/api/v1/timelines/tag")),
                new LemmyCollector(endpoint(restEndpoints, CollectionSource.LEMMY, "https://lemmy.world/api/v3/search")),
                new DevToCollector(endpoint(restEndpoints, CollectionSource.DEV_TO, "https://dev.to/api/articles")),
                new HackerNewsCollector(endpoint(restEndpoints, CollectionSource.HACKER_NEWS, "https://hn.algolia.com/api/v1/search")),
                new StackExchangeCollector(endpoint(restEndpoints, CollectionSource.STACK_EXCHANGE, "https://api.stackexchange.com/2.3/search")),
                new GitHubCollector(endpoint(restEndpoints, CollectionSource.GITHUB, "https://api.github.com/search/repositories")),
                new TabNewsCollector(endpoint(restEndpoints, CollectionSource.TABNEWS, "https://www.tabnews.com.br/api/v1/contents")),
                new WikipediaCollector(endpoint(restEndpoints, CollectionSource.WIKIPEDIA, "https://en.wikipedia.org/w/api.php")),
                new GitLabCollector(endpoint(restEndpoints, CollectionSource.GITLAB, "https://gitlab.com/api/v4/projects")),
                new LobstersCollector(endpoint(restEndpoints, CollectionSource.LOBSTERS, "https://lobste.rs")));
        var urls = new RssFeedLinkValidator();
        return new DefaultCollectionGateway(service, new BoundedFeedHttpFetcher(urls),
                new dev.henrique.rsscollector.application.RssFeedParser(urls), urls);
    }

    private static URI endpoint(Map<CollectionSource, URI> overrides, CollectionSource source, String production) {
        return overrides.getOrDefault(source, URI.create(production));
    }

    @Override
    public List<CollectedItemInput> collect(CollectionSource source, String input) {
        if (source == CollectionSource.ALPHA || source == CollectionSource.BETA) {
            var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            dev.henrique.collectorrunner.application.SocialSourceAdapter adapter = source == CollectionSource.ALPHA
                    ? new dev.henrique.collectorrunner.application.AlphaSocialSourceAdapter(mapper)
                    : new dev.henrique.collectorrunner.application.BetaSocialSourceAdapter(mapper);
            return adapter.parseFixture(input).stream().map(CollectedItemInput::fromSocialPost).toList();
        }
        if (source == CollectionSource.RSS) {
            if (!rssUrls.isValidFeedUrl(input)) throw new IllegalArgumentException("Invalid public feed URL");
            try {
                // Feed-local IDs are namespaced so two independent feeds can both publish item '1'.
                String namespace = sha256(input.trim()) + ":";
                return rssParser.parse(input, rssFetcher.fetch(input)).stream()
                        .map(item -> new CollectedItemInput("rss", namespace + item.id(), item.title(),
                                item.link().toString(), item.publishedAt())).toList();
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Feed collection interrupted");
            } catch (IOException exception) {
                throw new IllegalStateException("Feed collection failed");
            }
        }
        return collectors.collect(CollectorSource.valueOf(source.name()), input).stream()
                .map(item -> new CollectedItemInput(source.name().toLowerCase(java.util.Locale.ROOT).replace('_', '-'),
                        item.sourceId(), item.title(), item.link().toString(), item.publishedAt())).toList();
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }
}
