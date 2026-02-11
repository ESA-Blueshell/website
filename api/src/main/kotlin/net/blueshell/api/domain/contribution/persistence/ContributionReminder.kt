package net.blueshell.api.domain.contribution.persistence

import jakarta.persistence.*
import net.blueshell.api.shared.model.AuditedSoftDeleteEntity
import net.blueshell.api.shared.model.Identifiable
import net.blueshell.api.shared.model.asRef
import net.blueshell.api.user.persistence.User
import org.hibernate.Hibernate
import org.hibernate.annotations.SQLDelete
import org.hibernate.annotations.SQLRestriction

@Entity
@Table(
    name = "contribution_reminders",
    uniqueConstraints = [UniqueConstraint(
        name = "uk_contribution_reminders_user_period_deleted_at",
        columnNames = ["user_id", "contribution_period_id", "deleted_at"]
    )],
    indexes = [
        Index(name = "idx_contribution_reminders_deleted_at", columnList = "deleted_at"),
        Index(name = "idx_contribution_reminders_created_at", columnList = "created_at"),
        Index(name = "idx_contribution_reminders_user_id", columnList = "user_id, deleted_at"),
        Index(
            name = "idx_contribution_reminders_contribution_period_id",
            columnList = "contribution_period_id, deleted_at"
        )
    ]
)
@SQLDelete(
    sql = """
      UPDATE contribution_reminders
      SET deleted_at = NOW(), version = version + 1
      WHERE user_id = ? AND contribution_period_id = ? AND version = ?
    """
)
@SQLRestriction("deleted_at = '9999-12-31 23:59:59'")
class ContributionReminder(
    @EmbeddedId
    override var id: Id = Id(),
) : AuditedSoftDeleteEntity(), Identifiable<ContributionReminder.Id> {

    @MapsId("userId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    lateinit var user: User
        internal set

    val userId: Long
        get() = id.userId ?: 0

    @MapsId("contributionPeriodId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "contribution_period_id", nullable = false)
    lateinit var contributionPeriod: ContributionPeriod
        internal set

    val contributionPeriodId: Long
        get() = id.contributionPeriodId ?: 0

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null) return false
        if (Hibernate.getClass(this) != Hibernate.getClass(other)) return false
        other as ContributionReminder
        return id == other.id
    }

    override fun hashCode(): Int = id.hashCode()

    @Embeddable
    data class Id(
        var userId: Long? = null,
        var contributionPeriodId: Long? = null
    ) : java.io.Serializable

}
