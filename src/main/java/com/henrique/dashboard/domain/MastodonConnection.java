package com.henrique.dashboard.domain;

import lombok.Data;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** Conta Mastodon conectada por um usuário do dashboard. O token fica cifrado. */
@lombok.Getter
@lombok.NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
@Entity
@Table(name = "mastodon_connection")
@Data
public class MastodonConnection {
    @Id
    @Column(name = "user_id", length = 255)
    private String userId;

    @Column(nullable = false, length = 500)
    private String instance;

    @Column(name = "encrypted_access_token", nullable = false, length = 2048)
    private String encryptedAccessToken;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public MastodonConnection(String userId, String instance, String encryptedAccessToken, Instant createdAt) {
        this.userId = userId;
        this.instance = instance;
        this.encryptedAccessToken = encryptedAccessToken;
        this.createdAt = createdAt;
    }

    public void replaceToken(String instance, String encryptedAccessToken, Instant createdAt) {
        this.instance = instance;
        this.encryptedAccessToken = encryptedAccessToken;
        this.createdAt = createdAt;
    }
}
