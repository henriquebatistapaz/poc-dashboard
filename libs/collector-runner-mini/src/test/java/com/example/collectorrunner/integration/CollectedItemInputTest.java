package com.example.collectorrunner.integration;

import com.example.collectorrunner.domain.NormalizedSocialPost;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CollectedItemInputTest {
    @Test
    void mapsSocialPostWithoutInventingPublicationDate() {
        var input = CollectedItemInput.fromSocialPost(new NormalizedSocialPost(" demo ", " id-1 ", "author", "title", null, " https://example.org/1 "));
        assertEquals("demo", input.source());
        assertEquals("id-1", input.externalId());
        assertEquals("title", input.title());
        assertEquals("https://example.org/1", input.link());
        assertNull(input.publishedAt());
    }

    @Test
    void rejectsMissingFieldsAndOversizedValuesBeforeDatabaseAccess() {
        assertThrows(IllegalArgumentException.class, () -> new CollectedItemInput(" ", "id", null, "link", null));
        assertThrows(IllegalArgumentException.class, () -> new CollectedItemInput("s", null, null, "link", null));
        assertThrows(IllegalArgumentException.class, () -> new CollectedItemInput("s", "id", null, " ", null));
        assertThrows(IllegalArgumentException.class, () -> new CollectedItemInput("s".repeat(41), "id", null, "link", null));
        assertThrows(IllegalArgumentException.class, () -> new CollectedItemInput("s", "i".repeat(2001), null, "link", null));
        assertThrows(IllegalArgumentException.class, () -> new CollectedItemInput("s", "id", "t".repeat(1001), "link", null));
        assertThrows(IllegalArgumentException.class, () -> new CollectedItemInput("s", "id", null, "l".repeat(2001), null));
        assertThrows(IllegalArgumentException.class, () -> CollectedItemInput.fromSocialPost(null));
    }
}
