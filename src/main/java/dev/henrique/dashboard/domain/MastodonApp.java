package dev.henrique.dashboard.domain;

import lombok.Data;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** App OAuth registrado em uma instância Mastodon. O client_secret fica cifrado. */
@lombok.Getter
@lombok.NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
@Entity
@Table(name = "mastodon_app")
@Data
public class MastodonApp {
    @Id
    @Column(length = 500)
    private String instance;

    @Column(name = "client_id", nullable = false, length = 255)
    private String clientId;

    @Column(name = "encrypted_client_secret", nullable = false, length = 2048)
    private String encryptedClientSecret;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public MastodonApp(String instance, String clientId, String encryptedClientSecret, Instant createdAt) {
        this.instance = instance;
        this.clientId = clientId;
        this.encryptedClientSecret = encryptedClientSecret;
        this.createdAt = createdAt;
    }
}
