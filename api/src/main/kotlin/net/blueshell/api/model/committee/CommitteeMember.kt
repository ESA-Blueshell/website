package net.blueshell.api.model.committee

import jakarta.persistence.*
import net.blueshell.api.common.jpa.JpaListener
import net.blueshell.api.model.User
import net.blueshell.api.model.base.AuditedSoftDeleteEntity
import net.blueshell.api.model.base.Identifiable
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

    @get:Transient
    var committeeId: Long
        get() = requireNotNull(id.committeeId) { "committeeId is required" }
        set(value) {
            id.committeeId = value
        }

    @get:Transient
    var userId: Long
        get() = requireNotNull(id.userId) { "userId is required" }
        set(value) {
            id.userId = value
        }

    @field:MapsId("userId")
    @field:ManyToOne(fetch = FetchType.LAZY, optional = false)
    @field:JoinColumn(name = "user_id", nullable = false)
    lateinit var user: User

    @field:MapsId("committeeId")
    @field:ManyToOne(fetch = FetchType.LAZY, optional = false)
    @field:JoinColumn(name = "committee_id", nullable = false)
    lateinit var committee: Committee

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