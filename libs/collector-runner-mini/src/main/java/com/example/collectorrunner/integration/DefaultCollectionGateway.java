package com.example.collectorrunner.integration;

import com.example.rsscollector.application.BoundedFeedHttpFetcher;
import com.example.rsscollector.application.FeedDocumentFetcher;
import com.example.rsscollector.application.RssFeedLinkValidator;
import com.henrique.collector.feed.JdkHttpFeedFetcher;
import com.henrique.collector.source.*;
import java.io.IOException;
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
    private final com.example.rsscollector.application.RssFeedParser rssParser;
    private final RssFeedLinkValidator rssUrls;

    public static DefaultCollectionGateway fromEnvironment(Map<String, String> environment) {
        var fetcher = new JdkHttpFeedFetcher();
        var parser = new com.henrique.collector.feed.RssFeedParser();
        var transport = new JdkRedditHttpTransport();
        var redditConfig = RedditOAuthConfig.fromEnvironment(environment);
        var service = new CollectorService(new SourceRegistry(),
                new GoogleNewsCollector(fetcher, parser),
                new GoogleAlertsCollector(fetcher, parser, new GoogleAlertsUrlPolicy()),
                new WordPressFeedCollector(fetcher, parser, new PublicHttpsFeedUrlPolicy()),
                new RedditCollector(redditConfig, new RedditOAuthTokenProvider(redditConfig, transport), transport));
        var urls = new RssFeedLinkValidator();
        return new DefaultCollectionGateway(service, new BoundedFeedHttpFetcher(urls),
                new com.example.rsscollector.application.RssFeedParser(urls), urls);
    }

    @Override
    public List<CollectedItemInput> collect(CollectionSource source, String input) {
        if (source == CollectionSource.ALPHA || source == CollectionSource.BETA) {
            var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            com.example.collectorrunner.application.SocialSourceAdapter adapter = source == CollectionSource.ALPHA
                    ? new com.example.collectorrunner.application.AlphaSocialSourceAdapter(mapper)
                    : new com.example.collectorrunner.application.BetaSocialSourceAdapter(mapper);
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
