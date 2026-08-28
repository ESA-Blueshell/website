package net.blueshell.api.contribution.web

import io.swagger.v3.oas.annotations.media.Schema

@Schema(name = "CreateContributionReminderRequest")
data class CreateContributionReminderRequest(
    var userId: Long,

    var contributionPeriodId: Long
)
