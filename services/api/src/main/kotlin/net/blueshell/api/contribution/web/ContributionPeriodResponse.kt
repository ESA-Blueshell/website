package net.blueshell.api.contribution.web

import io.swagger.v3.oas.annotations.media.Schema
import java.time.Instant
import java.time.LocalDate

@Schema(name = "ContributionPeriodResponse")
data class ContributionPeriodResponse(
    var id: Long,
    var startDate: LocalDate,
    var endDate: LocalDate,
    var halfYearCutoffDate: LocalDate,
    var halfYearFee: Double,
    var fullYearFee: Double,
    var alumniFee: Double,
    var contactListId: Long? = null,
    var version: Long,
    var createdAt: Instant,
    var updatedAt: Instant
)
