package net.blueshell.api.domain.user.persistence

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
        Index(name = "idx_memberships_incasso", columnList = "incasso")
    ]
)
@SQLDelete(sql = "UPDATE memberships SET deleted_at = NOW(), version = version + 1 WHERE id = ? AND version = ?")
@SQLRestriction("deleted_at = '9999-12-31 23:59:59'")
class Membership : AuditedAutoIdEntity() {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    lateinit var user: User
        internal set

    val userId: Long
        get() = user.id ?: 0

    @Column(name = "start_date", nullable = false)
    var startDate: LocalDate = LocalDate.now()

    @Column(name = "end_date")
    var endDate: LocalDate? = null

    @Column(name = "type", nullable = false)
    @Enumerated(EnumType.STRING)
    var memberType = MemberType.REGULAR

    @Column(name = "incasso", nullable = false)
    var incasso = false
}