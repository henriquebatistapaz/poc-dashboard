package com.henrique.collector.source;

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
