package net.blueshell.api.model;

import jakarta.persistence.*;
import lombok.*;
import net.blueshell.api.base.BaseModel;
import net.blueshell.api.common.enums.ResetType;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import java.time.Instant;

@Entity
@Table(
        name = "recovery_tokens",
        uniqueConstraints = @UniqueConstraint(name = "uk_recovery_selector_deleted_at", columnNames = {"selector", "deleted_at"}),
        indexes = {
                @Index(name = "idx_recovery_tokens_user_id_type_deleted_at", columnList = "user_id,type,deleted_at"),
                @Index(name = "idx_recovery_tokens_expires", columnList = "expires_at")
        }
)
@Data
@EqualsAndHashCode(onlyExplicitlyIncluded = true, callSuper = true)
@NoArgsConstructor
@SQLDelete(sql = "UPDATE recovery_tokens SET deleted_at = NOW(), version = version + 1 WHERE id = ? AND version = ?")
@SQLRestriction("deleted_at = '9999-12-31 23:59:59'")
public class RecoveryToken extends BaseModel {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 50)
    private ResetType type;

    @Column(name = "selector", nullable = false, length = 64)
    private String selector;

    @Column(name = "verifier_hash", nullable = false, length = 255)
    private String verifierHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "consumed_at")
    private Instant consumedAt;

    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }

    public boolean isConsumed() {
        return consumedAt != null;
    }
}