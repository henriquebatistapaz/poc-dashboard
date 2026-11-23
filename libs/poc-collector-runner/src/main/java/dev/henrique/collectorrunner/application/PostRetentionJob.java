package dev.henrique.collectorrunner.application;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class PostRetentionJob {
    private static final Logger log = LoggerFactory.getLogger(PostRetentionJob.class);
    private final SocialPostPersistenceService posts;
    private final int retentionDays;

    public PostRetentionJob(SocialPostPersistenceService posts,
            @Value("${runner.retention-days:0}") int retentionDays) {
        if (retentionDays < 0) throw new IllegalArgumentException("Retention days must be zero or greater");
        this.posts = posts;
        this.retentionDays = retentionDays;
    }

    @Scheduled(cron = "0 17 3 * * *", zone = "UTC")
    public void pruneExpiredPosts() {
        if (retentionDays == 0) return;
        long removed = posts.deleteCreatedBefore(Instant.now().minus(retentionDays, ChronoUnit.DAYS));
        if (removed > 0) log.info("Post retention removed {} expired rows", removed);
    }
}
