package net.blueshell.api.dto.contribution

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotNull
import net.blueshell.api.dto.base.AuditedAutoIdDTO
import java.time.LocalDate

@Schema(name = "ContributionPeriod")
data class ContributionPeriodDTO(
    @field:NotNull
    var startDate: LocalDate,

    @field:NotNull
    var endDate: LocalDate,

    @field:NotNull
    var halfYearFee: Double = 0.0,

    @field:NotNull
    var fullYearFee: Double = 0.0,

    @field:NotNull
    var alumniFee: Double = 0.0,

    var listId: Long? = null
) : AuditedAutoIdDTO()
