package net.blueshell.api.model.committee

import jakarta.persistence.*
import net.blueshell.api.base.BaseModel
import net.blueshell.api.base.JpaListener
import net.blueshell.api.model.User
import org.hibernate.annotations.SQLDelete
import org.hibernate.annotations.SQLRestriction

@Entity
@Table(
    name = "committee_members",
    uniqueConstraints = [UniqueConstraint(
        name = "uk_committee_members_committee_user_deleted_at",
        columnNames = ["committee_id", "user_id", "deleted_at"]
    )],
    indexes = [Index(
        name = "idx_committee_members_deleted_at",
        columnList = "deleted_at"
    ), Index(
        name = "idx_committee_members_committee_id",
        columnList = "committee_id"
    ), Index(
        name = "idx_committee_members_user_id",
        columnList = "user_id"
    ), Index(name = "idx_committee_members_committee_role", columnList = "committee_id, role")]
)
@SQLDelete(sql = "UPDATE committee_members SET deleted_at = NOW(), version = version + 1 WHERE id = ? AND version = ?")
@SQLRestriction("deleted_at = '9999-12-31 23:59:59'")
@EntityListeners(JpaListener::class)
class CommitteeMember : BaseModel() {
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, updatable = false, insertable = false)
    lateinit var user: User

    @Column(name = "user_id", nullable = false)
    var userId: Long = 0

    @field:ManyToOne(optional = false, fetch = FetchType.LAZY)
    @field:JoinColumn(name = "committee_id", nullable = false, insertable = false, updatable = false)
    private var _committee: Committee? = null
    var committee: Committee
        get() = requireNotNull(_committee) { "Committee is required" }
        set(value) {
            _committee = value
            committeeId = value.id ?: committeeId
        }

    @Column(name = "committee_id", nullable = false)
    var committeeId: Long = 0

    @Column(name = "role", length = 255)
    var role: String? = null
}
