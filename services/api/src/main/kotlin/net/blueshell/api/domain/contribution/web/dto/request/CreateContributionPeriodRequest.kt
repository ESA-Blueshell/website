package net.blueshell.api.domain.contribution.web.dto.request

import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDate

@Schema(name = "CreateContributionPeriodRequest")
data class CreateContributionPeriodRequest(
    var startDate: LocalDate,

    var endDate: LocalDate,

    var halfYearFee: Double,

    var fullYearFee: Double,

    var alumniFee: Double,

    var contactListId: Long? = null
)
