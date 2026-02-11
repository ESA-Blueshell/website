package net.blueshell.api.committee.persistence

import jakarta.persistence.*
import net.blueshell.api.shared.model.AuditedSoftDeleteEntity
import net.blueshell.api.shared.model.Identifiable
import net.blueshell.api.user.persistence.User
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
            id.committeeId = value.id
        }

    val committeeId: Long get() = id.committeeId ?: 0

    @field:MapsId("userId")
    @field:ManyToOne(fetch = FetchType.LAZY, optional = false)
    @field:JoinColumn(name = "user_id", nullable = false)
    private var _user: User? = null
    var user: User
        get() = requireNotNull(_user) { "User is required" }
        set(value) {
            _user = value
            id.userId = value.id
        }

    val userId: Long get() = id.userId ?: 0

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
