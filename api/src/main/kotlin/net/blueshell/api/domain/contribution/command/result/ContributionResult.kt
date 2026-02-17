package net.blueshell.api.domain.contribution.command.result

import java.time.Instant

/**
 * Command result model for Contribution domain.
 * Matches the structure of ContributionResponse DTO (1:1 mapping).
 * Note: remindedAt field in DTO doesn't exist in entity, so it's omitted here.
 */
data class ContributionResult(
    val userId: Long,
    val contributionPeriodId: Long,
    val version: Long,
    val createdAt: Instant,
    val updatedAt: Instant,
    val deletedAt: Instant?
)
