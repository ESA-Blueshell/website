package net.blueshell.api.contribution.web

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Positive
import net.blueshell.api.shared.dto.bulk.BulkFeeType
import java.time.LocalDate

/**
 * Opens the fee cycle for a period: both dates, and any fee type the treasurer changed.
 *
 * There is no list of members and no list of amounts. Who is asked follows from the period,
 * and what they owe follows from the fee type and the period's fees — so the only thing the
 * treasurer can state here is a different *type* for a member, never a different amount.
 */
@Schema(name = "SendFeeCycleRequest")
data class SendFeeCycleRequest(
    @field:NotNull(message = "A contribution period is required.")
    @field:Positive
    val contributionPeriodId: Long? = null,

    @field:NotNull(message = "A payment due date is required.")
    @field:Schema(description = "The date the transfer group is asked to have paid by.")
    val paymentDueDate: LocalDate? = null,

    @field:NotNull(message = "A debit date is required.")
    @field:Schema(description = "The date the direct-debit group is told the money will be taken.")
    val debitDate: LocalDate? = null,

    @field:Schema(description = "Fee type per member, where the treasurer changed it from the one that applies.")
    val feeTypeOverrides: Map<Long, BulkFeeType> = emptyMap(),
)
