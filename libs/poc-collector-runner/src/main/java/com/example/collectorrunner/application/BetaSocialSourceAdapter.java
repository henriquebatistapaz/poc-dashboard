package com.example.collectorrunner.application;

import com.example.collectorrunner.domain.NormalizedSocialPost;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;

/** Maps the synthetic Beta fixture format into provider-independent posts. */
public final class BetaSocialSourceAdapter implements SocialSourceAdapter {
    private final ObjectMapper objectMapper;

    public BetaSocialSourceAdapter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public String sourceName() {
        return "beta";
    }

    @Override
    public List<NormalizedSocialPost> parseFixture(String fixture) {
        JsonNode items = FixtureJson.requiredArray(FixtureJson.read(objectMapper, fixture), "items");
        List<NormalizedSocialPost> normalized = new ArrayList<>();
        for (JsonNode item : items) {
            normalized.add(new NormalizedSocialPost(
                    sourceName(),
                    FixtureJson.requiredText(item, "postId"),
                    FixtureJson.requiredText(item, "authorName"),
                    FixtureJson.requiredText(item, "body"),
                    FixtureJson.requiredInstant(item, "publishedAt"),
                    FixtureJson.requiredText(item, "link")));
        }
        return List.copyOf(normalized);
    }
}
