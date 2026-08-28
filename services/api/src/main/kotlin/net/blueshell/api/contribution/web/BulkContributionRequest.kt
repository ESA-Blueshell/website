package net.blueshell.api.contribution.web

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Positive
import jakarta.validation.constraints.Size

/** Records a paid contribution for every selected user in one period. */
@Schema(name = "BulkMarkPaidRequest")
data class BulkMarkPaidRequest(
    @field:NotEmpty(message = "Select at least one user.")
    @field:Size(max = 1000, message = "Select at most 1000 users at a time.")
    val userIds: List<@Positive Long> = emptyList(),

    @field:NotNull(message = "A contribution period is required.")
    @field:Positive
    val contributionPeriodId: Long? = null,
)

/** Removes a recorded contribution for every selected user in one period. */
@Schema(name = "BulkMarkUnpaidRequest")
data class BulkMarkUnpaidRequest(
    @field:NotEmpty(message = "Select at least one user.")
    @field:Size(max = 1000, message = "Select at most 1000 users at a time.")
    val userIds: List<@Positive Long> = emptyList(),

    @field:NotNull(message = "A contribution period is required.")
    @field:Positive
    val contributionPeriodId: Long? = null,
)
