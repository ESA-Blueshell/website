package net.blueshell.api.contribution.web

import io.swagger.v3.oas.annotations.media.Schema
import java.sql.Timestamp
import java.time.Instant

@Schema(name = "ContributionResponse")
data class ContributionResponse(
    var userId: Long,
    var contributionPeriodId: Long,
    var remindedAt: Timestamp? = null,
    var version: Long,
    var createdAt: Instant,
    var updatedAt: Instant
)
