package net.blueshell.api.contribution.web

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.Positive

@Schema(name = "CreateContributionRequest")
data class CreateContributionRequest(
    @field:Positive(message = "User ID must be positive")
    var userId: Long,

    @field:Positive(message = "Contribution period ID must be positive")
    var contributionPeriodId: Long
)
