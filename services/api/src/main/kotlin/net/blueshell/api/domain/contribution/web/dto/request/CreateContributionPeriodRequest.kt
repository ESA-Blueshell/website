package net.blueshell.api.domain.contribution.web.dto.request

import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDate
import jakarta.validation.constraints.Future
import jakarta.validation.constraints.Positive
import jakarta.validation.constraints.PositiveOrZero

@Schema(name = "CreateContributionPeriodRequest")
data class CreateContributionPeriodRequest(
    var startDate: LocalDate,

    @field:Future(message = "End date must be in the future")
    var endDate: LocalDate,

    @field:Positive(message = "Half-year fee must be positive")
    var halfYearFee: Double,

    @field:Positive(message = "Full-year fee must be positive")
    var fullYearFee: Double,

    @field:PositiveOrZero(message = "Alumni fee must be positive or zero")
    var alumniFee: Double,

    var contactListId: Long? = null
)
