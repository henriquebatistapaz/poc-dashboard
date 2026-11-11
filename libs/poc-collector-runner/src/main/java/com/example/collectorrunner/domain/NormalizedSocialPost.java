package com.example.collectorrunner.domain;

import java.time.Instant;

/** Canonical synthetic post model. TODO: finalize required fields and validation in an SDD. */
public record NormalizedSocialPost(
        String source,
        String externalId,
        String author,
        String text,
        Instant publishedAt,
        String permalink) {
}
