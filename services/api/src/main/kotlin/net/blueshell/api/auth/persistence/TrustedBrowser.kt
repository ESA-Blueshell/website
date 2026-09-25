package net.blueshell.api.auth.persistence

import jakarta.persistence.Column
import jakarta.persistence.Entity
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

/** A browser told to skip the sign-in code until [expiresAt], thirty days from being trusted. */
@Entity
@Table(name = "trusted_browsers", indexes = [Index(name = "idx_trusted_browsers_user_id", columnList = "user_id")])
@SQLDelete(sql = "UPDATE trusted_browsers SET deleted_at = NOW(), version = version + 1 WHERE id = ? AND version = ?")
@SQLRestriction("deleted_at = '9999-12-31 23:59:59'")
class TrustedBrowser(
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    var user: User,
    @Column(name = "selector", nullable = false, length = 64)
    var selector: String,
    @Column(name = "verifier_hash", nullable = false, length = 64)
    var verifierHash: String,
    @Column(name = "browser_family", nullable = false, length = 64)
    var browserFamily: String,
    @Column(name = "browser_platform", nullable = false, length = 64)
    var browserPlatform: String,
    @Column(name = "trusted_at", nullable = false)
    var trustedAt: Instant,
    @Column(name = "expires_at", nullable = false)
    var expiresAt: Instant,
    @Column(name = "last_used_at")
    var lastUsedAt: Instant? = null,
) : AuditedAutoIdEntity()
