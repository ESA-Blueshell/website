package net.blueshell.api.domain.contribution.web.dto.request

import io.swagger.v3.oas.annotations.media.Schema

@Schema(name = "CreateContributionReminderRequest")
data class CreateContributionReminderRequest(
    var userId: Long,

    var contributionPeriodId: Long
)
