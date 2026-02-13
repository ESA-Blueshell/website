package net.blueshell.api.domain.contribution.web.dto.response

import io.swagger.v3.oas.annotations.media.Schema
import net.blueshell.api.shared.dto.AuditedSoftDeleteDTO
import java.sql.Timestamp

@Schema(name = "ContributionReminderResponse")
data class ContributionReminderResponse(
    var userId: Long? = null,
    var contributionPeriodId: Long? = null,
    var remindedAt: Timestamp? = null
) : AuditedSoftDeleteDTO()
