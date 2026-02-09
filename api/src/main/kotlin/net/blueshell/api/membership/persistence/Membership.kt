package net.blueshell.api.membership.persistence

import jakarta.persistence.*
import net.blueshell.api.user.persistence.User
import net.blueshell.api.shared.enums.MemberType
import net.blueshell.api.shared.jpa.JpaListener
import net.blueshell.api.shared.model.AuditedAutoIdEntity
import net.blueshell.api.shared.model.asRef
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
@EntityListeners(JpaListener::class)
class Membership : AuditedAutoIdEntity() {
    @field:JoinColumn(name = "user_id", nullable = false)
    @field:ManyToOne(fetch = FetchType.LAZY, optional = false)
    private var _user: User? = null
    var user: User
        get() = requireNotNull(_user) { "User is required" }
        set(value) {
            _user = value
            userId = _user?.id ?: userId
        }

    @field:Column(name = "user_id", nullable = false, updatable = false, insertable = false)
    var userId: Long = 0
        get() = _user?.id ?: field
        set(value) {
            field = value
            // Only override the reference, if the ref exists and is different from current
            if (value != 0L && value != _user?.id) {
                _user = User::class.asRef(value)
            }
        }

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
