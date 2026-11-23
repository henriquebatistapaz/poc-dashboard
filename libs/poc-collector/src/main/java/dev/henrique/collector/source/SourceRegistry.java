package dev.henrique.collector.source;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public final class SourceRegistry {
    private final Map<CollectorSource, SourceCapability> capabilities = new EnumMap<>(CollectorSource.class);

    public SourceRegistry() {
        register(CollectorSource.GOOGLE_NEWS, CapabilityStatus.CONDITIONAL,
                "Observed public RSS route; Google does not publish an API contract for it.");
        register(CollectorSource.WORDPRESS, CapabilityStatus.AVAILABLE,
                "Searches public WordPress.com tag RSS feeds for the supplied keyword.");
        register(CollectorSource.MASTODON, CapabilityStatus.AVAILABLE, "Uses the public Mastodon.social hashtag timeline.");
        register(CollectorSource.LEMMY, CapabilityStatus.AVAILABLE, "Uses the public Lemmy.world post search API.");
        register(CollectorSource.DEV_TO, CapabilityStatus.AVAILABLE, "Uses the public DEV.to articles API.");
        register(CollectorSource.HACKER_NEWS, CapabilityStatus.AVAILABLE, "Uses the public Hacker News Algolia search API.");
        register(CollectorSource.STACK_EXCHANGE, CapabilityStatus.AVAILABLE, "Uses the public Stack Exchange API for Stack Overflow.");
        register(CollectorSource.GITHUB, CapabilityStatus.AVAILABLE, "Uses the public GitHub repository search API.");
        register(CollectorSource.TABNEWS, CapabilityStatus.AVAILABLE, "Uses the public TabNews contents API.");
        register(CollectorSource.WIKIPEDIA, CapabilityStatus.AVAILABLE, "Uses the public Wikipedia search API.");
        register(CollectorSource.GITLAB, CapabilityStatus.AVAILABLE, "Uses the public GitLab projects API.");
        register(CollectorSource.LOBSTERS, CapabilityStatus.AVAILABLE, "Uses the public Lobsters tag and hottest APIs.");
        register(CollectorSource.ISSUU, CapabilityStatus.UNAVAILABLE,
                "Issuu's first-party API requires an account token and does not document public publication search; no public feed route is verified.");
    }

    public SourceCapability get(CollectorSource source) {
        return capabilities.get(source);
    }

    public List<SourceCapability> all() {
        return List.copyOf(capabilities.values());
    }

    private void register(CollectorSource source, CapabilityStatus status, String reason) {
        capabilities.put(source, new SourceCapability(source, status, reason));
    }
}
