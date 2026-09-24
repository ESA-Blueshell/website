package net.blueshell.api.auth.persistence

import io.swagger.v3.oas.annotations.media.Schema
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

/** One change to how somebody signs in, or one thing that happened to their sign-ins. Kept twelve months. */
@Entity
@Table(
    name = "security_events",
    indexes = [
        Index(name = "idx_security_events_deleted_at", columnList = "deleted_at"),
        Index(name = "idx_security_events_subject_occurred_at", columnList = "subject_user_id, occurred_at"),
        Index(name = "idx_security_events_occurred_at", columnList = "occurred_at"),
    ],
)
@SQLDelete(sql = "UPDATE security_events SET deleted_at = NOW(), version = version + 1 WHERE id = ? AND version = ?")
@SQLRestriction("deleted_at = '9999-12-31 23:59:59'")
class SecurityEvent(
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "subject_user_id", nullable = false)
    var subject: User,
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actor_user_id")
    var actor: User? = null,
    @Enumerated(EnumType.STRING)
    @Column(name = "actor_kind", nullable = false, length = 16)
    var actorKind: SecurityActorKind,
    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, length = 64)
    var kind: SecurityEventKind,
    @Column(name = "note", length = 1023)
    var note: String? = null,
    @Column(name = "browser_family", length = 64)
    var browserFamily: String? = null,
    @Column(name = "browser_platform", length = 64)
    var browserPlatform: String? = null,
    @Column(name = "occurred_at", nullable = false)
    var occurredAt: Instant,
) : AuditedAutoIdEntity()

@Schema(enumAsRef = true)
enum class SecurityActorKind {
    PERSON,
    SYSTEM,
    OPERATOR,
}

/**
 * Values are persisted in `security_events.kind`, so they are schema and must not be renamed.
 * A kind that notifies sends the person a security notification with a lock link; one that
 * tells administrators emails every admin, because one may have to act.
 */
@Schema(enumAsRef = true)
enum class SecurityEventKind(
    val notifies: Boolean,
    val tellsAdministrators: Boolean = false,
) {
    SIGNED_IN(false),
    NEW_BROWSER(true),
    SIGN_IN_REUSED(true),
    SIGN_IN_BROWSER_CHANGED(true),
    CODE_LIMIT_REACHED(true),
    PASSWORD_RESET(true),
    PASSWORD_CHANGED(true),
    EMAIL_CHANGE_REQUESTED(true),
    EMAIL_CHANGED(false),
    EMAIL_CHANGED_BY_BOARD(true),
    TWO_FACTOR_ON(true),
    TWO_FACTOR_OFF(true),
    TWO_FACTOR_REPLACED(true),
    BACKUP_CODES_REGENERATED(true),
    BACKUP_CODE_USED(true),
    TRUSTED_BROWSER_ADDED(true),
    TWO_FACTOR_RESET(true),
    REENROLLED(false),
    ACCOUNT_LOCKED(false, tellsAdministrators = true),
    BREAK_GLASS(false, tellsAdministrators = true),
    ACCOUNT_UNLOCKED(true),
    SIGNED_OUT_EVERYWHERE(false),
    SIGNED_OUT_ELSEWHERE(false),
    ROLES_CHANGED(false),
}
