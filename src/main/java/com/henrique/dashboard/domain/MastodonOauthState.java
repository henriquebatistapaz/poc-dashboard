package com.henrique.dashboard.domain;

import lombok.Data;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** Estado pendente de um login OAuth. Uso único e com prazo curto. */
@lombok.Getter
@lombok.NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
@Entity
@Table(name = "mastodon_oauth_state")
@Data
public class MastodonOauthState {
    @Id
    @Column(length = 64)
    private String state;

    @Column(name = "user_id", nullable = false, length = 255)
    private String userId;

    @Column(nullable = false, length = 500)
    private String instance;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    public MastodonOauthState(String state, String userId, String instance, Instant expiresAt) {
        this.state = state;
        this.userId = userId;
        this.instance = instance;
        this.expiresAt = expiresAt;
    }
}
