package com.example.collectorrunner.persistence;

import com.example.collectorrunner.domain.NormalizedSocialPost;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;

@lombok.Getter
@lombok.NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
@Entity
@Table(name = "social_posts", uniqueConstraints = @UniqueConstraint(
        name = "uk_social_posts_source_external_id", columnNames = {"source", "external_id"}))
public class SocialPostEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String source;

    @Column(name = "external_id", nullable = false, length = 500)
    private String externalId;

    @Column(length = 500)
    private String author;

    @Column(columnDefinition = "text")
    private String text;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(length = 2048)
    private String permalink;


    public SocialPostEntity(NormalizedSocialPost post) {
        this(post, Instant.now());
    }

    SocialPostEntity(NormalizedSocialPost post, Instant createdAt) {
        this.source = post.source().trim();
        this.externalId = post.externalId().trim();
        this.author = post.author();
        this.text = post.text();
        this.publishedAt = post.publishedAt();
        this.permalink = post.permalink();
        this.createdAt = createdAt;
    }

    public NormalizedSocialPost toDomain() {
        return new NormalizedSocialPost(source, externalId, author, text, publishedAt, permalink);
    }
}
