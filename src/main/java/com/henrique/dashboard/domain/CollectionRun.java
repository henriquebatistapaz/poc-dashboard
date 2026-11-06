package com.henrique.dashboard.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** One collection request. The input text is never stored, only the source and the outcome. */
@Entity
@Table(name = "collection_run")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CollectionRun {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 40)
    private String source;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RunStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    private Integer fetched;
    private Integer inserted;
    private Integer duplicates;

    @Column(name = "error_message", length = 300)
    private String errorMessage;

    public CollectionRun(String source, Instant createdAt) {
        this.source = source;
        this.status = RunStatus.PENDING;
        this.createdAt = createdAt;
    }

    public void markRunning(Instant now) {
        this.status = RunStatus.RUNNING;
        this.startedAt = now;
    }

    public void markSucceeded(Instant now, int fetched, int inserted, int duplicates) {
        this.status = RunStatus.SUCCEEDED;
        this.finishedAt = now;
        this.fetched = fetched;
        this.inserted = inserted;
        this.duplicates = duplicates;
    }

    public void markFailed(Instant now, String message) {
        this.status = RunStatus.FAILED;
        this.finishedAt = now;
        this.errorMessage = message;
    }

}
