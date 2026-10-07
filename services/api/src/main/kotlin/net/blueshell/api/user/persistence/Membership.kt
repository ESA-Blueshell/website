package net.blueshell.api.user.persistence

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.Index
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import net.blueshell.api.shared.enums.MemberType
import net.blueshell.api.shared.model.AuditedAutoIdEntity
import net.blueshell.api.shared.model.SoftDelete
import org.hibernate.annotations.SQLDelete
import org.hibernate.annotations.SQLRestriction
import java.time.LocalDate

@Entity
@Table(
    name = "memberships",
    indexes = [
        Index(name = "idx_memberships_deleted_at", columnList = "deleted_at"),
        Index(name = "idx_memberships_user_id", columnList = "user_id"),
        Index(name = "idx_memberships_start_date", columnList = "start_date"),
        Index(name = "idx_memberships_end_date", columnList = "end_date"),
        Index(name = "idx_memberships_member_type", columnList = "type"),
    ],
)
@SQLDelete(sql = "UPDATE memberships SET ${SoftDelete.STAMP}, version = version + 1 WHERE id = ? AND version = ?")
@SQLRestriction(SoftDelete.ACTIVE)
class Membership(
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    var user: User,
    @Column(name = "start_date", nullable = false)
    var startDate: LocalDate,
    @Column(name = "end_date")
    var endDate: LocalDate? = null,
    @Column(name = "type", nullable = false)
    @Enumerated(EnumType.STRING)
    var memberType: MemberType = MemberType.REGULAR,
    /** The day it became active; null while it waits for its first contribution (api ADR-036). */
    @Column(name = "activated_on")
    var activatedOn: LocalDate? = null,
) : AuditedAutoIdEntity() {
    /** Running and not yet paid for, so it carries no member role. */
    val isPending: Boolean
        get() = activatedOn == null && endDate == null

    val userId: Long
        get() = user.id ?: 0
}
