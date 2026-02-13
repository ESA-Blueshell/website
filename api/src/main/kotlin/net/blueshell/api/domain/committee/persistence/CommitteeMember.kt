package net.blueshell.api.domain.committee.persistence

import jakarta.persistence.*
import net.blueshell.api.domain.user.persistence.User
import net.blueshell.api.shared.model.AuditedSoftDeleteEntity
import net.blueshell.api.shared.model.Identifiable
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
    @MapsId("committeeId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "committee_id", nullable = false)
    lateinit var committee: Committee
        internal set

    val committeeId: Long
        get() = id.committeeId ?: 0

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, insertable = false, updatable = false)
    lateinit var user: User
        internal set

    val userId: Long
        get() = id.userId ?: 0

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
