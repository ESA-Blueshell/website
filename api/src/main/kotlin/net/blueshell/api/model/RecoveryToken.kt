package net.blueshell.api.model

import jakarta.persistence.*
import net.blueshell.api.common.enums.ResetType
import net.blueshell.api.model.base.AuditedAutoIdEntity
import org.hibernate.annotations.SQLDelete
import org.hibernate.annotations.SQLRestriction
import java.time.Instant

@Entity
@Table(
    name = "recovery_tokens",
    uniqueConstraints = [
        UniqueConstraint(name = "uk_recovery_selector_deleted_at", columnNames = ["selector", "deleted_at"])
    ],
    indexes = [
        Index(name = "idx_recovery_tokens_user_id_type_deleted_at", columnList = "user_id,type,deleted_at"),
        Index(name = "idx_recovery_tokens_expires", columnList = "expires_at")
    ]
)
@SQLDelete(sql = "UPDATE recovery_tokens SET deleted_at = NOW(), version = version + 1 WHERE id = ? AND version = ?")
@SQLRestriction("deleted_at = '9999-12-31 23:59:59'")
class RecoveryToken : AuditedAutoIdEntity() {
    @field:ManyToOne(fetch = FetchType.LAZY, optional = false)
    @field:JoinColumn(name = "user_id", nullable = false)
    private var _user: User? = null
    var user: User
        get() = requireNotNull(_user) { "User is required" }
        set(value) {
            _user = value
        }

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 50)
    lateinit var type: ResetType

    @Column(name = "selector", nullable = false, length = 64)
    lateinit var selector: String

    @Column(name = "verifier_hash", nullable = false, length = 255)
    lateinit var verifierHash: String

    @Column(name = "expires_at", nullable = false)
    lateinit var expiresAt: Instant

    @Column(name = "consumed_at")
    var consumedAt: Instant? = null

    val isExpired: Boolean
        get() = Instant.now().isAfter(expiresAt)

    val isConsumed: Boolean
        get() = consumedAt != null
}
