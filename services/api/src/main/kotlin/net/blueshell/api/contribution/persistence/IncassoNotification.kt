package net.blueshell.api.contribution.persistence

import jakarta.persistence.*
import net.blueshell.api.shared.dto.bulk.BulkFeeType
import net.blueshell.api.shared.model.AuditedSoftDeleteEntity
import net.blueshell.api.shared.model.Identifiable
import net.blueshell.api.user.persistence.User
import org.hibernate.Hibernate
import org.hibernate.annotations.SQLDelete
import org.hibernate.annotations.SQLRestriction
import java.time.Instant
import java.time.LocalDate

/**
 * A direct-debit pre-notification: the statement that money will be taken, and when.
 *
 * Its own record rather than a flag on [ContributionReminder], because a pre-notification
 * and a payment request are different statements and the treasurer needs to know which a
 * member received. Modelled on the reminder, which already records a member, a period and
 * when it was sent.
 */
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

    /** The fee type this pre-notification stated, and so the reason its email gave. */
    @Enumerated(EnumType.STRING)
    @Column(name = "fee_type", nullable = false, length = 32)
    var feeType: BulkFeeType,

    /**
     * The amount this pre-notification said would be taken. Stored rather than derived from
     * [feeType] and the period, because the period's fees are editable and this is a record
     * of what the member was told.
     */
    @Column(name = "amount", nullable = false)
    var amount: Double,

    /** The date the money was said to be taken on. */
    @Column(name = "debit_date", nullable = false)
    var debitDate: LocalDate,

    /**
     * When the member was notified. Its own column rather than `updatedAt`, which any later
     * touch of the row would move.
     */
    @Column(name = "asked_at", nullable = false)
    var askedAt: Instant = Instant.now(),
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
