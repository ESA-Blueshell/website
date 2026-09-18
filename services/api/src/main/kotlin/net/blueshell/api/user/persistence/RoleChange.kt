package net.blueshell.api.user.persistence

import jakarta.persistence.*
import net.blueshell.api.shared.enums.Role
import net.blueshell.api.shared.model.AuditedAutoIdEntity
import org.hibernate.annotations.SQLDelete
import org.hibernate.annotations.SQLRestriction
import java.time.Instant

/**
 * One admin's change to the roles one person holds.
 *
 * Both sides are stored whole rather than as a delta: the reader's question is what somebody
 * held before and after, and a delta answers it only by replaying every row since. Only changes
 * made through the roles endpoint are here — a derived role moving with its membership or its
 * committee seat is recorded by that membership or that seat.
 */
@Entity
@Table(
    name = "role_changes",
    indexes = [
        Index(name = "idx_role_changes_deleted_at", columnList = "deleted_at"),
        Index(name = "idx_role_changes_subject_changed_at", columnList = "subject_user_id, changed_at"),
    ],
)
@SQLDelete(sql = "UPDATE role_changes SET deleted_at = NOW(), version = version + 1 WHERE id = ? AND version = ?")
@SQLRestriction("deleted_at = '9999-12-31 23:59:59'")
class RoleChange(
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "subject_user_id", nullable = false)
    var subject: User,
    /** Who made the change. Always a person: the endpoint is admin-only. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "actor_user_id", nullable = false)
    var actor: User,
    @Convert(converter = RoleSetConverter::class)
    @Column(name = "roles_before", nullable = false, length = 255)
    var rolesBefore: Set<Role>,
    @Convert(converter = RoleSetConverter::class)
    @Column(name = "roles_after", nullable = false, length = 255)
    var rolesAfter: Set<Role>,
    /** Why, in the admin's own words. Optional, so a routine handover is not slowed down. */
    @Column(name = "note", length = 1023)
    var note: String? = null,
    @Column(name = "changed_at", nullable = false)
    var changedAt: Instant = Instant.now(),
) : AuditedAutoIdEntity()
