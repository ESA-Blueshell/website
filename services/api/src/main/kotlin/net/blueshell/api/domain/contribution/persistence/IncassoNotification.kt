package net.blueshell.api.domain.contribution.persistence

import jakarta.persistence.*
import net.blueshell.api.domain.user.persistence.User
import net.blueshell.api.shared.model.AuditedSoftDeleteEntity
import net.blueshell.api.shared.model.Identifiable
import org.hibernate.Hibernate
import org.hibernate.annotations.SQLDelete
import org.hibernate.annotations.SQLRestriction
import java.time.LocalDate

@Entity
@Table(
    name = "incasso_notifications",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uk_incasso_notifications_user_period_deleted_at",
            columnNames = ["user_id", "contribution_period_id", "deleted_at"]
        ),
    ],
    indexes = [
        Index(name = "idx_incasso_notifications_deleted_at", columnList = "deleted_at"),
        Index(name = "idx_incasso_notifications_created_at", columnList = "created_at"),
        Index(name = "idx_incasso_notifications_user_id", columnList = "user_id, deleted_at"),
        Index(
            name = "idx_incasso_notifications_contribution_period_id",
            columnList = "contribution_period_id, deleted_at"
        )
    ]
)
@SQLDelete(
    sql = """
      UPDATE incasso_notifications
      SET deleted_at = NOW(), version = version + 1
      WHERE contribution_period_id = ? AND user_id = ? AND version = ?
    """
)
@SQLRestriction("deleted_at = '9999-12-31 23:59:59'")
class IncassoNotification(
    @EmbeddedId
    override var id: Id = Id(),

    @MapsId("userId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    var user: User,

    @MapsId("contributionPeriodId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "contribution_period_id", nullable = false)
    var contributionPeriod: ContributionPeriod,

    @Column(name = "amount", nullable = true)
    var amount: Double? = null,

    @Column(name = "expected_incasso_date", nullable = true)
    var expectedIncassoDate: LocalDate? = null,
) : AuditedSoftDeleteEntity(), Identifiable<IncassoNotification.Id> {

    val userId: Long
        get() = id.userId ?: user.id ?: 0

    val contributionPeriodId: Long
        get() = id.contributionPeriodId ?: contributionPeriod.id ?: 0

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null) return false
        if (Hibernate.getClass(this) != Hibernate.getClass(other)) return false
        other as IncassoNotification
        return id == other.id
    }

    override fun hashCode(): Int = id.hashCode()

    @Embeddable
    data class Id(
        var userId: Long? = null,
        var contributionPeriodId: Long? = null
    ) : java.io.Serializable

}
