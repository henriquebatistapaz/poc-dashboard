package com.example.collectorrunner.application;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class PostRetentionJobTest {
    @Test
    void rejectsNegativeRetention() {
        assertThrows(IllegalArgumentException.class, () -> new PostRetentionJob(mock(SocialPostPersistenceService.class), -1));
    }

    @Test
    void retentionIsDisabledByDefault() {
        SocialPostPersistenceService posts = mock(SocialPostPersistenceService.class);
        new PostRetentionJob(posts, 0).pruneExpiredPosts();
        verify(posts, never()).deleteCreatedBefore(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void deletesOnlyRowsOlderThanConfiguredCutoff() {
        SocialPostPersistenceService posts = mock(SocialPostPersistenceService.class);
        when(posts.deleteCreatedBefore(org.mockito.ArgumentMatchers.any())).thenReturn(3L);
        Instant before = Instant.now().minusSeconds(30L * 24 * 60 * 60);

        new PostRetentionJob(posts, 30).pruneExpiredPosts();

        var captor = org.mockito.ArgumentCaptor.forClass(Instant.class);
        verify(posts).deleteCreatedBefore(captor.capture());
        assertTrue(captor.getValue().isAfter(before.minusSeconds(2)));
        assertTrue(captor.getValue().isBefore(Instant.now()));
    }
}
