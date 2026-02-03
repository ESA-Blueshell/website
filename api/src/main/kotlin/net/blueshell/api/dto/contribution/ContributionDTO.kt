package net.blueshell.api.dto.contribution

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotNull
import net.blueshell.api.base.BaseDTO
import java.sql.Timestamp

@Schema(name = "Contribution")
data class ContributionDTO(
    @field:NotNull
    var userId: Long? = null,

    @field:NotNull
    var contributionPeriodId: Long? = null,

    var remindedAt: Timestamp? = null
) : BaseDTO()
