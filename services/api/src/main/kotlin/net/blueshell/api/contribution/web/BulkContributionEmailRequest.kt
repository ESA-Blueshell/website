package net.blueshell.api.contribution.web

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.Future
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Positive
import jakarta.validation.constraints.Size
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
 * One thing can be stated per member: which fee *type* prices their reminder. Never an amount,
 * which follows from the type and the period.
 */
@Schema(name = "SendPaymentEmailsRequest")
data class SendPaymentEmailsRequest(
    @field:NotNull(message = "A contribution period is required.")
    @field:Positive
    val contributionPeriodId: Long? = null,
    @field:NotEmpty(message = "Select at least one member.")
    @field:Size(max = MAX_SELECTION, message = "Select at most $MAX_SELECTION members.")
    val userIds: List<@Positive Long> = emptyList(),
    @field:Size(max = MAX_SELECTION, message = "Tick at most $MAX_SELECTION members back in.")
    @field:Schema(
        description =
            "Warned members this request overrules. Every one of them must be in the " +
                "selection and must be somebody the send writes to.",
    )
    val forciblyIncludedUserIds: List<@Positive Long> = emptyList(),
    @field:Future(message = "A payment due date must be after today.")
    @field:Schema(description = "Required exactly when somebody here is sent a reminder.")
    val paymentDueDate: LocalDate? = null,
    @field:Size(max = MAX_SELECTION, message = "Choose a fee for at most $MAX_SELECTION members.")
    @field:Schema(description = "Fee type per member, where the treasurer changed it.")
    val feeTypeOverrides: Map<Long, BulkFeeType> = emptyMap(),
)
