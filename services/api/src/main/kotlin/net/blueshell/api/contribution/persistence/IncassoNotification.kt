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
 * One telling of one member, before the money is taken, what will be taken and when.
 *
 * Its own record rather than a flag on [ContributionReminder]: a pre-notification and a payment
 * request are different statements, and the treasurer needs to know which a member received.
 * One row per telling, since a debit date that moves has to be re-notified.
 */
@Entity
@Table(
    name = "incasso_notifications",
    indexes = [
        Index(name = "idx_incasso_notifications_deleted_at", columnList = "deleted_at"),
        Index(name = "idx_incasso_notifications_created_at", columnList = "created_at"),
        Index(name = "idx_incasso_notifications_user_id", columnList = "user_id, deleted_at"),
        Index(
            name = "idx_incasso_notifications_contribution_period_id",
            columnList = "contribution_period_id, deleted_at"
        ),
        Index(
            name = "idx_incasso_notifications_user_period_asked",
            columnList = "user_id, contribution_period_id, asked_at"
        )
    ]
)
@SQLDelete(sql = "UPDATE incasso_notifications SET deleted_at = NOW(), version = version + 1 WHERE id = ? AND version = ?")
@SQLRestriction("deleted_at = '9999-12-31 23:59:59'")
class IncassoNotification(
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    var user: User,

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

    /** When the member was notified. Fixed at the notification. */
    @Column(name = "asked_at", nullable = false, updatable = false)
    var askedAt: Instant = Instant.now(),
) : AuditedAutoIdEntity() {

    val userId: Long
        get() = user.id ?: 0

    val contributionPeriodId: Long
        get() = contributionPeriod.id ?: 0
}
