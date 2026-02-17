package net.blueshell.api.domain.contribution.web.dto.request

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotNull
import java.time.LocalDate

@Schema(name = "UpdateContributionPeriodRequest")
data class UpdateContributionPeriodRequest(
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

    var listId: Long? = null,

    @field:NotNull
    var version: Long? = null
)
