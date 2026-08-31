package net.blueshell.api.contribution.persistence

import jakarta.persistence.*
import net.blueshell.api.shared.dto.bulk.BulkFeeType
import net.blueshell.api.user.persistence.User
import net.blueshell.api.shared.model.AuditedSoftDeleteEntity
import net.blueshell.api.shared.model.Identifiable
import org.hibernate.Hibernate
import org.hibernate.annotations.SQLDelete
import org.hibernate.annotations.SQLRestriction
import java.time.Instant
import java.time.LocalDate

@Entity
@Table(
    name = "contribution_reminders",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uk_contribution_reminders_user_period_deleted_at",
            columnNames = ["user_id", "contribution_period_id", "deleted_at"]
        ),
    ],
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
      WHERE contribution_period_id = ? AND user_id = ? AND version = ?
    """
)
@SQLRestriction("deleted_at = '9999-12-31 23:59:59'")
class ContributionReminder(
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

    /**
     * The fee type this request stated, so the email's reason is the true one rather than a
     * guess recovered from an amount. Null on the rows written before the fee cycle existed,
     * and by the single-member reminder, which quotes the period's fee options instead of one
     * amount and therefore states no single reason.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "fee_type", length = 32)
    var feeType: BulkFeeType? = null,

    /**
     * The amount this request asked for. Stored rather than derived from [feeType] and the
     * period, because the period's fees are editable: deriving it would let a change of next
     * year's fee rewrite what last year's email is recorded as having said. Null wherever
     * [feeType] is.
     */
    @Column(name = "amount")
    var amount: Double? = null,

    /** The date this request asked to be paid by. Null wherever [feeType] is. */
    @Column(name = "payment_due_date")
    var paymentDueDate: LocalDate? = null,

    /**
     * When the member was asked. Its own column rather than `updatedAt`, which any later
     * touch of the row would move, and which would then misreport when the member was asked.
     */
    @Column(name = "asked_at", nullable = false)
    var askedAt: Instant = Instant.now(),
) : AuditedSoftDeleteEntity(), Identifiable<ContributionReminder.Id> {

    val userId: Long
        get() = id.userId ?: user.id ?: 0

    val contributionPeriodId: Long
        get() = id.contributionPeriodId ?: contributionPeriod.id ?: 0

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
