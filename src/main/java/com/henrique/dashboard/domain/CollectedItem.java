package com.henrique.dashboard.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;

/** Read model for items written by collector-runner-mini into collected_item. */
@lombok.Getter
@lombok.NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
@Entity
@Table(name = "collected_item",
        uniqueConstraints = @UniqueConstraint(name = "uk_collected_item_source_external", columnNames = {"source", "external_id"}))
public class CollectedItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 40)
    private String source;

    @Column(name = "external_id", nullable = false, length = 2000)
    private String externalId;

    @Column(length = 1000)
    private String title;

    @Column(nullable = false, length = 2000)
    private String link;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "collected_at", nullable = false)
    private Instant collectedAt;


    public CollectedItem(String source, String externalId, String title, String link,
                         Instant publishedAt, Instant collectedAt) {
        this.source = source;
        this.externalId = externalId;
        this.title = title;
        this.link = link;
        this.publishedAt = publishedAt;
        this.collectedAt = collectedAt;
    }

}
