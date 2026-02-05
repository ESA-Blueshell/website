package net.blueshell.api.dto.contribution

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotNull
import net.blueshell.api.dto.base.AuditedSoftDeleteDTO
import java.sql.Timestamp

@Schema(name = "ContributionReminder")
data class ContributionReminderDTO(
    @field:NotNull
    var userId: Long,

    @field:NotNull
    var contributionPeriodId: Long,

    var remindedAt: Timestamp? = null
) : AuditedSoftDeleteDTO()
