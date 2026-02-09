package net.blueshell.api.feature.contribution.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotNull
import net.blueshell.api.shared.dto.AuditedAutoIdDTO
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
