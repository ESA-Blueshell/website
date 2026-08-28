package net.blueshell.api.contribution.web

import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDate
import jakarta.validation.constraints.Positive
import jakarta.validation.constraints.PositiveOrZero

@Schema(name = "UpdateContributionPeriodRequest")
data class UpdateContributionPeriodRequest(
    var startDate: LocalDate,

    var endDate: LocalDate,

    @field:Positive(message = "Half-year fee must be positive")
    var halfYearFee: Double,

    @field:Positive(message = "Full-year fee must be positive")
    var fullYearFee: Double,

    @field:PositiveOrZero(message = "Alumni fee must be positive or zero")
    var alumniFee: Double,

    var contactListId: Long? = null,

    var version: Long
)
