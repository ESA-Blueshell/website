package net.blueshell.api.auth.persistence

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.Index
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import net.blueshell.api.shared.model.AuditedAutoIdEntity
import net.blueshell.api.user.persistence.User
import org.hibernate.annotations.SQLDelete
import org.hibernate.annotations.SQLRestriction
import java.time.Instant

/**
 * An authenticator app's secret, encrypted (api ADR-031). A person holds at most one [ACTIVE] and
 * one being set up beside it, so replacing an app never leaves them without one.
 */
@Entity
@Table(
    name = "two_factor_secrets",
    indexes = [Index(name = "idx_two_factor_secrets_user_id_state", columnList = "user_id, state")],
)
@SQLDelete(sql = "UPDATE two_factor_secrets SET deleted_at = NOW(), version = version + 1 WHERE id = ? AND version = ?")
@SQLRestriction("deleted_at = '9999-12-31 23:59:59'")
class TwoFactorSecret(
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    var user: User,
    @Column(name = "ciphertext", nullable = false, length = 255)
    var ciphertext: String,
    @Column(name = "key_id", nullable = false, length = 32)
    var keyId: String,
    @Enumerated(EnumType.STRING)
    @Column(name = "state", nullable = false, length = 16)
    var state: TwoFactorSecretState = TwoFactorSecretState.PENDING,
    /** The last time step a code was accepted for; a code at or before it is a replay. */
    @Column(name = "last_used_step")
    var lastUsedStep: Long? = null,
    @Column(name = "confirmed_at")
    var confirmedAt: Instant? = null,
    @Column(name = "activated_at")
    var activatedAt: Instant? = null,
) : AuditedAutoIdEntity()

enum class TwoFactorSecretState {
    /** Shown to the person; no code has proved it yet. */
    PENDING,

    /** A right code proved it and its backup codes were shown; not on until they are saved. */
    CONFIRMED,

    ACTIVE,
}
