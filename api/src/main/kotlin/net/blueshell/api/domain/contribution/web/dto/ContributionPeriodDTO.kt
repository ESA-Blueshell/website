package net.blueshell.api.domain.contribution.web.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotNull
import net.blueshell.api.shared.dto.AuditedAutoIdDTO
import java.time.LocalDate

@Schema(name = "ContributionPeriod")
data class ContributionPeriodDTO(
    @field:NotNull
    var startDate: LocalDate? = null,

    @field:NotNull
    var endDate: LocalDate? = null,

    @field:NotNull
    var halfYearFee: Double? = null,

    @field:NotNull
    var fullYearFee: Double? = null,

    @field:NotNull
    var alumniFee: Double? = null,

    var listId: Long? = null
) : AuditedAutoIdDTO()
