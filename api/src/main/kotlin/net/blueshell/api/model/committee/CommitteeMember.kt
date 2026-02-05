package net.blueshell.api.model.committee

import jakarta.persistence.*
import net.blueshell.api.base.JpaListener
import net.blueshell.api.base.entity.AuditedSoftDeleteEntity
import net.blueshell.api.base.entity.Identifiable
import net.blueshell.api.model.User
import org.hibernate.Hibernate
import org.hibernate.annotations.SQLDelete
import org.hibernate.annotations.SQLRestriction

@Entity
@Table(
    name = "committee_members",
    indexes = [
        Index(name = "idx_committee_members_deleted_at", columnList = "deleted_at"),
        Index(name = "idx_committee_members_committee_id", columnList = "committee_id"),
        Index(name = "idx_committee_members_user_id", columnList = "user_id"),
        Index(name = "idx_committee_members_committee_role", columnList = "committee_id, role")
    ]
)
@SQLRestriction("deleted_at = '9999-12-31 23:59:59'")
@SQLDelete(
    sql = """
      UPDATE committee_members
      SET deleted_at = NOW(), version = version + 1
      WHERE committee_id = ? AND user_id = ? AND version = ?
    """
)
@EntityListeners(JpaListener::class)
class CommitteeMember(
    @EmbeddedId
    override var id: CommitteeMemberId = CommitteeMemberId()
) : AuditedSoftDeleteEntity(), Identifiable<CommitteeMemberId> {

    @get:Transient
    @set:Transient
    var committeeId: Long
        get() = requireNotNull(id.committeeId) { "committeeId is required" }
        set(value) {
            id.committeeId = value
        }

    @get:Transient
    @set:Transient
    var userId: Long
        get() = requireNotNull(id.userId) { "userId is required" }
        set(value) {
            id.userId = value
        }

    @field:MapsId("userId")
    @field:ManyToOne(fetch = FetchType.LAZY, optional = false)
    @field:JoinColumn(name = "user_id", nullable = false)
    private var _user: User? = null
    var user: User
        get() = requireNotNull(_user) { "User is required" }
        set(value) {
            _user = value
            value.id?.let { userId = it }
        }

    @field:MapsId("committeeId")
    @field:ManyToOne(fetch = FetchType.LAZY, optional = false)
    @field:JoinColumn(name = "committee_id", nullable = false)
    private var _committee: Committee? = null
    var committee: Committee
        get() = requireNotNull(_committee) { "Committee is required" }
        set(value) {
            _committee = value
            value.id?.let { committeeId = it }
        }

    @Column(name = "role", length = 255)
    var role: String? = null

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null) return false
        if (Hibernate.getClass(this) != Hibernate.getClass(other)) return false
        other as CommitteeMember
        return id == other.id
    }

    override fun hashCode(): Int = id.hashCode()
}

@Embeddable
data class CommitteeMemberId(
    var committeeId: Long? = null,
    var userId: Long? = null
) : java.io.Serializable
