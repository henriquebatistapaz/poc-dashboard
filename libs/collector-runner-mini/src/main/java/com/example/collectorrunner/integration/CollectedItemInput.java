package com.example.collectorrunner.integration;

import com.example.collectorrunner.domain.NormalizedSocialPost;
import java.time.Instant;

/** Input for dashboard-mini's collected_item contract; migration ownership stays with the dashboard. */
public record CollectedItemInput(String source, String externalId, String title, String link, Instant publishedAt) {
    public CollectedItemInput {
        source = required(source, "source", 40);
        externalId = required(externalId, "externalId", 2000);
        link = required(link, "link", 2000);
        if (title != null && title.length() > 1000) {
            throw new IllegalArgumentException("title exceeds 1000 characters");
        }
    }

    /** Social text becomes the display title; longer text requires an explicit caller-selected excerpt. */
    public static CollectedItemInput fromSocialPost(NormalizedSocialPost post) {
        if (post == null) throw new IllegalArgumentException("post is required");
        return new CollectedItemInput(post.source(), post.externalId(), post.text(), post.permalink(), post.publishedAt());
    }

    private static String required(String value, String name, int maximum) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required");
        String normalized = value.trim();
        if (normalized.length() > maximum) throw new IllegalArgumentException(name + " exceeds " + maximum + " characters");
        return normalized;
    }
}
