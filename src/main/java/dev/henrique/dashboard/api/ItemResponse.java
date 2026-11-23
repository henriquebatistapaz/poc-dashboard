package dev.henrique.dashboard.api;

import dev.henrique.dashboard.domain.CollectedItem;
import java.time.Instant;

public record ItemResponse(Long id, String source, String externalId, String title, String link,
                           Instant publishedAt, Instant collectedAt) {
    public static ItemResponse from(CollectedItem item) {
        return new ItemResponse(item.getId(), item.getSource(), item.getExternalId(), item.getTitle(),
                item.getLink(), item.getPublishedAt(), item.getCollectedAt());
    }
}
