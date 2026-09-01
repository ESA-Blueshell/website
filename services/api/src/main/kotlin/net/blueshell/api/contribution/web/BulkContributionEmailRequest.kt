package net.blueshell.api.contribution.web

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Positive
import jakarta.validation.constraints.Size
import net.blueshell.api.contribution.domain.ContributionEmailKind
import net.blueshell.api.shared.dto.bulk.BulkFeeType
import java.time.LocalDate

private const val MAX_SELECTION = 1000

@Schema(name = "BulkContributionEmailPreviewRequest")
data class BulkContributionEmailPreviewRequest(
    @field:NotNull(message = "A contribution period is required.")
    @field:Positive
    val contributionPeriodId: Long? = null,

    @field:NotEmpty(message = "Select at least one member.")
    @field:Size(max = MAX_SELECTION, message = "Select at most $MAX_SELECTION members.")
    val userIds: List<@Positive Long> = emptyList(),
)

/**
 * Two things can be stated per member and nothing else: which email they get, and which fee
 * *type* prices it. Never an amount — that follows from the type and the period.
 */
@Schema(name = "SendPaymentEmailsRequest")
data class SendPaymentEmailsRequest(
    @field:NotNull(message = "A contribution period is required.")
    @field:Positive
    val contributionPeriodId: Long? = null,

    @field:NotEmpty(message = "Select at least one member.")
    @field:Size(max = MAX_SELECTION, message = "Select at most $MAX_SELECTION members.")
    val userIds: List<@Positive Long> = emptyList(),

    @field:Schema(
        description = "Warned members the operator ticked back in. A hard-excluded member " +
            "named here is still not written to.",
    )
    val forciblyIncludedUserIds: List<Long> = emptyList(),

    @field:Schema(description = "Members moved off the email their direct-debit flag chose.")
    val kindOverrides: Map<Long, ContributionEmailKind> = emptyMap(),

    @field:Schema(description = "Required exactly when somebody here is getting a payment request.")
    val paymentDueDate: LocalDate? = null,

    @field:Schema(description = "Required exactly when somebody here is getting a pre-notification.")
    val debitDate: LocalDate? = null,

    @field:Schema(description = "Fee type per member, where the treasurer changed it.")
    val feeTypeOverrides: Map<Long, BulkFeeType> = emptyMap(),
)
