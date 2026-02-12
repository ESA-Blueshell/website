package net.blueshell.api.domain.contribution.web.dto

import io.swagger.v3.oas.annotations.media.Schema
import net.blueshell.api.shared.dto.AuditedSoftDeleteDTO
import java.sql.Timestamp

@Schema(name = "ContributionResponse")
data class ContributionResponse(
    var userId: Long? = null,
    var contributionPeriodId: Long? = null,
    var remindedAt: Timestamp? = null
) : AuditedSoftDeleteDTO()
