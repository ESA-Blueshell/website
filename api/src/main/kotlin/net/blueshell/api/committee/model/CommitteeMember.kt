package net.blueshell.api.committee.model

import jakarta.persistence.*
import net.blueshell.api.shared.jpa.JpaListener
import net.blueshell.api.user.model.User
import net.blueshell.api.shared.model.AuditedSoftDeleteEntity
import net.blueshell.api.shared.model.Identifiable
import net.blueshell.api.shared.model.asRef
import org.hibernate.Hibernate
import org.hibernate.annotations.SQLDelete
import org.hibernate.annotations.SQLRestriction
import java.io.Serializable

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
    @AttributeOverrides(AttributeOverride(name = "userId", column = Column(name = "user_id", nullable = false)))
    override var id: Id = Id(),
) : AuditedSoftDeleteEntity(), Identifiable<CommitteeMember.Id> {
    @field:MapsId("committeeId")
    @field:ManyToOne(fetch = FetchType.LAZY, optional = false)
    @field:JoinColumn(name = "committee_id", nullable = false)
    private var _committee: Committee? = null
    var committee: Committee
        get() = requireNotNull(_committee) { "Committee is required" }
        set(value) {
            _committee = value
            committeeId = value.id ?: committeeId
        }

    @Column(name = "committee_id", nullable = false, updatable = false, insertable = false)
    var committeeId: Long = 0
        get() = id.committeeId ?: field
        set(value) {
            field = value
            id.committeeId = value
            // Only override the reference, if the ref exists and is different from current
            if (value != 0L && value != _committee?.id) {
                _committee = Committee::class.asRef(value)
            }
        }

    @field:MapsId("userId")
    @field:ManyToOne(fetch = FetchType.LAZY, optional = false)
    @field:JoinColumn(name = "user_id", nullable = false)
    private var _user: User? = null
    var user: User
        get() = requireNotNull(_user) { "User is required" }
        set(value) {
            _user = value
            userId = _user?.id ?: userId
        }

    @field:Column(name = "user_id", nullable = false, updatable = false, insertable = false)
    var userId: Long = 0
        get() = id.userId ?: field
        set(value) {
            field = value
            id.userId = value
            // Only override the reference, if the ref exists and is different from current
            if (value != 0L && value != _user?.id) {
                _user = User::class.asRef(value)
            }
        }

    @Column(name = "role", length = 255)
    var role: String? = null

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null) return false
        if (Hibernate.getClass(this) != Hibernate.getClass(other)) return false
        other as CommitteeMember

        if (!this.id.isComplete || !other.id.isComplete) return false
        return this.id == other.id
    }

    override fun hashCode(): Int =
        if (id.isComplete) id.hashCode() else System.identityHashCode(this)

    @Embeddable
    data class Id(
        var committeeId: Long? = null,
        var userId: Long? = null
    ) : Serializable {
        @get:Transient
        val isComplete: Boolean
            get() = committeeId != null && userId != null
    }
}
