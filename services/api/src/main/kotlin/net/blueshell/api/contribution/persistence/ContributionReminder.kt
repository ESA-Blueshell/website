package net.blueshell.api.contribution.persistence

import jakarta.persistence.*
import net.blueshell.api.shared.dto.bulk.BulkFeeType
import net.blueshell.api.shared.model.AuditedAutoIdEntity
import net.blueshell.api.user.persistence.User
import org.hibernate.annotations.SQLDelete
import org.hibernate.annotations.SQLRestriction
import java.time.Instant
import java.time.LocalDate

/**
 * One asking of one member to pay for one period.
 *
 * A row per ask, not per member and period. The treasurer chases: a member can be asked in
 * September, again in February and again the week after, and each of those is a thing that
 * happened. Collapsing them into one row makes a member asked three times indistinguishable
 * from one asked once, which is the question this record exists to answer.
 */
@Entity
@Table(
    name = "contribution_reminders",
    indexes = [
        Index(name = "idx_contribution_reminders_deleted_at", columnList = "deleted_at"),
        Index(name = "idx_contribution_reminders_created_at", columnList = "created_at"),
        Index(name = "idx_contribution_reminders_user_id", columnList = "user_id, deleted_at"),
        Index(
            name = "idx_contribution_reminders_contribution_period_id",
            columnList = "contribution_period_id, deleted_at"
        ),
        Index(
            name = "idx_contribution_reminders_user_period_asked",
            columnList = "user_id, contribution_period_id, asked_at"
        )
    ]
)
@SQLDelete(sql = "UPDATE contribution_reminders SET deleted_at = NOW(), version = version + 1 WHERE id = ? AND version = ?")
@SQLRestriction("deleted_at = '9999-12-31 23:59:59'")
class ContributionReminder(
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    var user: User,

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
     * year's fee rewrite what this email is recorded as having said. Null wherever [feeType] is.
     */
    @Column(name = "amount")
    var amount: Double? = null,

    /** The date this request asked to be paid by. Null wherever [feeType] is. */
    @Column(name = "payment_due_date")
    var paymentDueDate: LocalDate? = null,

    /** When the member was asked. Fixed at the ask, so a later touch of the row cannot move it. */
    @Column(name = "asked_at", nullable = false, updatable = false)
    var askedAt: Instant = Instant.now(),
) : AuditedAutoIdEntity() {

    val userId: Long
        get() = user.id ?: 0

    val contributionPeriodId: Long
        get() = contributionPeriod.id ?: 0
}
