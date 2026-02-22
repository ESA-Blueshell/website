package net.blueshell.api.domain.contribution.web.dto.response

import io.swagger.v3.oas.annotations.media.Schema
import java.sql.Timestamp
import java.time.Instant

@Schema(name = "ContributionReminderResponse")
data class ContributionReminderResponse(
    var userId: Long,
    var contributionPeriodId: Long,
    var remindedAt: Timestamp? = null,
    var version: Long,
    var createdAt: Instant,
    var updatedAt: Instant
)
