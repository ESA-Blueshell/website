package net.blueshell.api.model.committee

import jakarta.persistence.*
import net.blueshell.api.base.JpaListener
import net.blueshell.api.base.entity.AuditedSoftDeleteEntity
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
    var id: CommitteeMemberId = CommitteeMemberId()

) : AuditedSoftDeleteEntity() {

    @MapsId("userId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    lateinit var user: User

    @MapsId("committeeId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "committee_id", nullable = false)
    lateinit var committee: Committee

    @Column(name = "role", length = 255)
    var role: String? = null

    fun setRelations(committee: Committee, user: User) {
        this.committee = committee
        this.user = user
        this.id = CommitteeMemberId(
            committeeId = committee.id ?: id.committeeId,
            userId = user.id ?: id.userId
        )
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null) return false
        if (Hibernate.getClass(this) != Hibernate.getClass(other)) return false
        other as CommitteeMember
        return id == other.id
    }

    override fun hashCode(): Int = id.hashCode()
}
