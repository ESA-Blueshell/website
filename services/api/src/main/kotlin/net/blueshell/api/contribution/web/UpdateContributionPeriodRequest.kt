package net.blueshell.api.contribution.web

import com.fasterxml.jackson.annotation.JsonIgnore
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.AssertTrue
import java.time.LocalDate
import jakarta.validation.constraints.Positive
import jakarta.validation.constraints.PositiveOrZero

@Schema(name = "UpdateContributionPeriodRequest")
data class UpdateContributionPeriodRequest(
    var startDate: LocalDate,

    var endDate: LocalDate,

    @field:Schema(description = "A regular membership starting after this date pays the half-year fee.")
    var halfYearCutoffDate: LocalDate,

    @field:Positive(message = "Half-year fee must be positive")
    var halfYearFee: Double,

    @field:Positive(message = "Full-year fee must be positive")
    var fullYearFee: Double,

    @field:PositiveOrZero(message = "Alumni fee must be positive or zero")
    var alumniFee: Double,

    var contactListId: Long? = null,

    var version: Long
) {
    @get:JsonIgnore
    @get:Schema(hidden = true)
    @get:AssertTrue(message = "The half-year cutoff must fall within the contribution period")
    val cutoffIsInThePeriod: Boolean
        get() = cutoffWithinPeriod(halfYearCutoffDate, startDate, endDate)
}
