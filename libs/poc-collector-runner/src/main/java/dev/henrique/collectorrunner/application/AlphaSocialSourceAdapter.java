package dev.henrique.collectorrunner.application;

import dev.henrique.collectorrunner.domain.NormalizedSocialPost;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;

/** Maps the synthetic Alpha fixture format into provider-independent posts. */
public final class AlphaSocialSourceAdapter implements SocialSourceAdapter {
    private final ObjectMapper objectMapper;

    public AlphaSocialSourceAdapter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public String sourceName() {
        return "alpha";
    }

    @Override
    public List<NormalizedSocialPost> parseFixture(String fixture) {
        JsonNode posts = FixtureJson.requiredArray(FixtureJson.read(objectMapper, fixture), "posts");
        List<NormalizedSocialPost> normalized = new ArrayList<>();
        for (JsonNode post : posts) {
            normalized.add(new NormalizedSocialPost(
                    sourceName(),
                    FixtureJson.requiredText(post, "id"),
                    FixtureJson.requiredText(post.path("author"), "handle"),
                    FixtureJson.requiredText(post, "text"),
                    FixtureJson.requiredInstant(post, "created_at"),
                    FixtureJson.requiredText(post, "url")));
        }
        return List.copyOf(normalized);
    }
}
