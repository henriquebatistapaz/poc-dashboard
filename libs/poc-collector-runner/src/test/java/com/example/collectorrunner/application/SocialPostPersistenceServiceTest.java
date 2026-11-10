package com.example.collectorrunner.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.example.collectorrunner.domain.NormalizedSocialPost;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class SocialPostPersistenceServiceTest {
    @Autowired private SocialPostPersistenceService service;

    @Test
    void persistsNewPostsAndRejectsDuplicatesBySourceAndExternalId() {
        NormalizedSocialPost post = post("alpha", "post-1", "first");
        NormalizedSocialPost duplicate = post("alpha", "post-1", "updated payload");
        assertEquals(List.of(post), service.acceptNew(List.of(post)));
        assertEquals(List.of(), service.acceptNew(List.of(duplicate)));
        assertEquals(List.of(post), service.findAll());
    }

    @Test
    void identityIncludesSourceAndOrderIsNewestPublicationFirst() {
        NormalizedSocialPost older = post("alpha", "same-id", "older");
        NormalizedSocialPost newer = post("beta", "same-id", "newer");
        service.acceptNew(List.of(older, newer));
        assertEquals(List.of(newer, older), service.findAll());
    }

    @Test
    void validatesWholeBatchBeforeSavingAndRejectsInvalidIdentity() {
        NormalizedSocialPost good = post("alpha", "one", "good");
        NormalizedSocialPost missingId = post("alpha", " ", "invalid");
        assertThrows(IllegalArgumentException.class, () -> service.acceptNew(List.of(good, missingId)));
        assertEquals(List.of(), service.findAll());
        assertThrows(IllegalArgumentException.class, () -> service.acceptNew(null));
        assertThrows(IllegalArgumentException.class, () -> service.acceptNew(Collections.singletonList(null)));
    }

    @Test
    void enforcesDatabaseFieldLengths() {
        assertThrows(IllegalArgumentException.class, () -> service.acceptNew(List.of(post("x".repeat(101), "id", "bad"))));
        assertThrows(IllegalArgumentException.class, () -> service.acceptNew(List.of(post("alpha", "x".repeat(501), "bad"))));
    }

    private NormalizedSocialPost post(String source, String id, String text) {
        Instant publishedAt = text.equals("older") ? Instant.parse("2026-01-01T00:00:00Z")
                : text.equals("newer") ? Instant.parse("2026-01-02T00:00:00Z")
                : Instant.parse("2026-01-01T00:00:00Z");
        return new NormalizedSocialPost(source, id, "author", text, publishedAt, "https://example.test/" + id);
    }
}
