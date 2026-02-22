package net.blueshell.api.domain.contribution.web.dto.request

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotNull

@Schema(name = "CreateContributionReminderRequest")
data class CreateContributionReminderRequest(
    @field:NotNull
    var userId: Long? = null,

    @field:NotNull
    var contributionPeriodId: Long? = null
)
