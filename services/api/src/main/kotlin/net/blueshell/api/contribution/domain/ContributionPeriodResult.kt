package net.blueshell.api.contribution.domain

import java.time.Instant
import java.time.LocalDate

/**
 * Command result model for ContributionPeriod domain.
 * Matches the structure of ContributionPeriodResponse DTO (1:1 mapping).
 */
data class ContributionPeriodResult(
    val id: Long,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val halfYearFee: Double,
    val fullYearFee: Double,
    val alumniFee: Double,
    val contactListId: Long?,
    val version: Long,
    val createdAt: Instant,
    val updatedAt: Instant,
    val deletedAt: Instant?
)
