package dev.henrique.collectorrunner.application;

import dev.henrique.collectorrunner.domain.NormalizedSocialPost;
import java.util.List;

/** Converts one synthetic source fixture format to the canonical model. */
public interface SocialSourceAdapter {
    String sourceName();

    /** @throws IllegalArgumentException when the fixture is malformed or contains invalid records. */
    List<NormalizedSocialPost> parseFixture(String fixture);
}
