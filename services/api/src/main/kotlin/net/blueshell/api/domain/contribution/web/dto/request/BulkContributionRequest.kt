package net.blueshell.api.domain.contribution.web.dto.request

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.NotNull
import net.blueshell.api.domain.contribution.command.BulkContributionOperation

@Schema(name = "BulkContributionRequest")
data class BulkContributionRequest(
    @field:NotEmpty
    var userIds: List<Long> = emptyList(),

    @field:NotNull
    var contributionPeriodId: Long? = null,

    @field:NotNull
    var operation: BulkContributionOperation? = null,
)
