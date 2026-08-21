package net.blueshell.api.domain.contribution.web.dto.request

import io.swagger.v3.oas.annotations.media.Schema

@Schema(name = "CreateContributionRequest")
data class CreateContributionRequest(
    var userId: Long,

    var contributionPeriodId: Long
)
