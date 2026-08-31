package net.blueshell.api.contribution.domain

import net.blueshell.api.shared.dto.bulk.BulkFeeType
import java.time.Instant
import java.time.LocalDate

/**
 * One asking of one member to pay for one period.
 *
 * Carries what the ask stated, so reading the record back answers what the member was told
 * rather than only that something was sent. A member can appear more than once for a period.
 */
data class ContributionReminderResult(
    val id: Long,
    val userId: Long,
    val contributionPeriodId: Long,
    /** Null on an ask that quoted the period's fee options rather than one amount. */
    val feeType: BulkFeeType?,
    val amount: Double?,
    val paymentDueDate: LocalDate?,
    val askedAt: Instant,
    val version: Long,
    val createdAt: Instant,
    val updatedAt: Instant,
    val deletedAt: Instant?
)
