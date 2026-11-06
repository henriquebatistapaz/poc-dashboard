package com.henrique.collector.source;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public final class SourceRegistry {
    private final Map<CollectorSource, SourceCapability> capabilities = new EnumMap<>(CollectorSource.class);

    public SourceRegistry() {
        register(CollectorSource.GOOGLE_NEWS, CapabilityStatus.CONDITIONAL,
                "Observed public RSS route; Google does not publish an API contract for it.");
        register(CollectorSource.GOOGLE_ALERTS, CapabilityStatus.CONDITIONAL,
                "Requires a user-provided RSS URL exposed by that user's Google Alerts account.");
        register(CollectorSource.WORDPRESS, CapabilityStatus.AVAILABLE,
                "Consumes a public RSS or Atom URL supplied by the user, if the site publishes one.");
        register(CollectorSource.REDDIT, CapabilityStatus.CONDITIONAL,
                "Official OAuth collector; disabled until Reddit Data API access is approved and OAuth credentials are configured.");
        register(CollectorSource.ISSUU, CapabilityStatus.UNVERIFIED,
                "No official public tokenless feed or API contract has been verified.");
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
