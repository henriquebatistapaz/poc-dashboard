package dev.henrique.dashboard.domain;

import lombok.Data;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** Registro de auditoria da remoção manual de um item (exceção prevista na spec, seção 6.1). */
@lombok.Getter
@lombok.NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
@Entity
@Table(name = "item_removal")
@Data
public class ItemRemoval {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 40)
    private String source;

    @Column(name = "external_id", nullable = false, length = 2000)
    private String externalId;

    @Column(nullable = false, length = 2000)
    private String link;

    @Column(nullable = false, length = 500)
    private String reason;

    @Column(name = "removed_by", nullable = false, length = 255)
    private String removedBy;

    @Column(name = "removed_at", nullable = false)
    private Instant removedAt;

    public ItemRemoval(String source, String externalId, String link, String reason, String removedBy, Instant removedAt) {
        this.source = source;
        this.externalId = externalId;
        this.link = link;
        this.reason = reason;
        this.removedBy = removedBy;
        this.removedAt = removedAt;
    }
}
