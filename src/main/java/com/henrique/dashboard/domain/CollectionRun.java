package com.henrique.dashboard.domain;

import lombok.Data;
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

/** One collection request. Input is encrypted at rest and exposed only through authenticated history. */
@Entity
@Table(name = "collection_run")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Data
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

    @Column(name = "scheduled_at")
    private Instant scheduledAt;

    @Column(name = "encrypted_input", length = 8192)
    private String encryptedInput;

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

    public CollectionRun(String source, Instant createdAt, Instant scheduledAt, String encryptedInput) {
        this(source, createdAt);
        this.scheduledAt = scheduledAt;
        this.encryptedInput = encryptedInput;
    }

    public void clearEncryptedInput() { this.encryptedInput = null; }

    public void retryScheduleAfterRestart() {
        this.status = RunStatus.PENDING;
        this.startedAt = null;
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
