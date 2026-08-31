package net.blueshell.api.contribution.web

import io.swagger.v3.oas.annotations.media.Schema
import net.blueshell.api.shared.dto.bulk.BulkFeeType
import java.time.Instant
import java.time.LocalDate

/**
 * One asking of one member to pay for one period.
 *
 * A member can appear more than once for the same period: the treasurer chases, and each
 * ask is its own row with its own `askedAt`.
 */
@Schema(name = "ContributionReminderResponse")
data class ContributionReminderResponse(
    var id: Long,
    var userId: Long,
    var contributionPeriodId: Long,

    @field:Schema(description = "When the member was asked.")
    var askedAt: Instant,

    @field:Schema(description = "The fee type this ask stated. Absent when it quoted the period's options instead.")
    var feeType: BulkFeeType? = null,

    @field:Schema(description = "The amount this ask asked for, as stated. Absent wherever the fee type is.")
    var amount: Double? = null,

    @field:Schema(description = "The date this ask asked to be paid by. Absent wherever the fee type is.")
    var paymentDueDate: LocalDate? = null,

    var version: Long,
    var createdAt: Instant,
    var updatedAt: Instant
)
