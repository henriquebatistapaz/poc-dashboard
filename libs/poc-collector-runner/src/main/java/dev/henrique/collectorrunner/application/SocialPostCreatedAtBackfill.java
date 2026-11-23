package dev.henrique.collectorrunner.application;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class SocialPostCreatedAtBackfill implements ApplicationRunner {
    private final JdbcTemplate jdbc;

    public SocialPostCreatedAtBackfill(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void run(ApplicationArguments args) {
        jdbc.update("""
                update social_posts
                   set created_at = coalesce(published_at, current_timestamp)
                 where created_at is null
                """);
    }
}
