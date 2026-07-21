package net.blueshell.api.domain.contribution.web.dto.request

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.Future
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Positive
import net.blueshell.api.shared.dto.bulk.BulkFeeType
import java.time.LocalDate

/**
 * Preview request for the contribution-reminder email. Renders (never sends) the email
 * for a single user, using the same fee type and payment-due date a bulk send would use.
 */
@Schema(name = "ContributionReminderPreviewRequest")
data class ContributionReminderPreviewRequest(
    @field:NotNull(message = "User ID is required")
    @field:Positive(message = "User ID must be positive")
    val userId: Long? = null,

    @field:NotNull(message = "Contribution period ID is required")
    @field:Positive(message = "Contribution period ID must be positive")
    val contributionPeriodId: Long? = null,

    @field:NotNull(message = "Fee type is required")
    val feeType: BulkFeeType? = null,

    @field:NotNull(message = "Payment due date is required")
    @field:Future(message = "Payment due date must be in the future")
    val paymentDueDate: LocalDate? = null,
)

/**
 * Preview request for the incasso-notification email. Renders (never sends) the email for
 * a single user, using the same fee type and expected incasso date a bulk send would use.
 */
@Schema(name = "IncassoNotificationPreviewRequest")
data class IncassoNotificationPreviewRequest(
    @field:NotNull(message = "User ID is required")
    @field:Positive(message = "User ID must be positive")
    val userId: Long? = null,

    @field:NotNull(message = "Contribution period ID is required")
    @field:Positive(message = "Contribution period ID must be positive")
    val contributionPeriodId: Long? = null,

    @field:NotNull(message = "Fee type is required")
    val feeType: BulkFeeType? = null,

    @field:NotNull(message = "Expected incasso date is required")
    @field:Future(message = "Expected incasso date must be in the future")
    val expectedIncassoDate: LocalDate? = null,
)
