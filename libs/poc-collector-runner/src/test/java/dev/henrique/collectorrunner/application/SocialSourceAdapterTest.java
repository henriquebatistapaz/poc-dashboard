package dev.henrique.collectorrunner.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.henrique.collectorrunner.domain.NormalizedSocialPost;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class SocialSourceAdapterTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void alphaFixtureMapsToCanonicalPost() throws IOException {
        AlphaSocialSourceAdapter adapter = new AlphaSocialSourceAdapter(objectMapper);
        assertEquals("alpha", adapter.sourceName());
        List<NormalizedSocialPost> posts = adapter
                .parseFixture(readFixture("/fixtures/alpha-posts.json"));

        assertEquals(List.of(new NormalizedSocialPost(
                "alpha", "alpha-001", "sample_author", "Synthetic post from Alpha",
                Instant.parse("2026-01-15T10:30:00Z"),
                "https://alpha.example.test/posts/alpha-001")), posts);
    }

    @Test
    void betaFixtureMapsDifferentFieldNamesToSameCanonicalModel() throws IOException {
        BetaSocialSourceAdapter adapter = new BetaSocialSourceAdapter(objectMapper);
        assertEquals("beta", adapter.sourceName());
        List<NormalizedSocialPost> posts = adapter
                .parseFixture(readFixture("/fixtures/beta-posts.json"));

        assertEquals(List.of(new NormalizedSocialPost(
                "beta", "beta-001", "Sample Author", "Synthetic post from Beta",
                Instant.parse("2026-01-16T11:45:00Z"),
                "https://beta.example.test/posts/beta-001")), posts);
    }

    @Test
    void malformedTimestampIsRejectedWithFieldContext() {
        String fixture = """
                {"posts":[{"id":"x","author":{"handle":"a"},"text":"t","created_at":"yesterday","url":"https://example.test/x"}]}
                """;

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> new AlphaSocialSourceAdapter(objectMapper).parseFixture(fixture));

        assertEquals("Field must be an ISO-8601 instant: created_at", error.getMessage());
    }

    @Test
    void rejectsInvalidJsonMissingArraysAndRequiredFields() {
        AlphaSocialSourceAdapter alpha = new AlphaSocialSourceAdapter(objectMapper);
        BetaSocialSourceAdapter beta = new BetaSocialSourceAdapter(objectMapper);

        assertThrows(IllegalArgumentException.class, () -> alpha.parseFixture("{"));
        assertThrows(IllegalArgumentException.class, () -> alpha.parseFixture("[]"));
        assertThrows(IllegalArgumentException.class, () -> alpha.parseFixture("{}"));
        assertThrows(IllegalArgumentException.class, () -> alpha.parseFixture("{\"posts\":[{}]}"));
        assertThrows(IllegalArgumentException.class, () -> beta.parseFixture("{\"items\":[{}]}"));
    }

    private String readFixture(String path) throws IOException {
        try (InputStream stream = getClass().getResourceAsStream(path)) {
            if (stream == null) throw new IOException("Fixture not found: " + path);
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
