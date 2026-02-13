package net.blueshell.api.domain.contribution.command.result

import net.blueshell.api.domain.contribution.persistence.Contribution
import net.blueshell.api.domain.contribution.persistence.ContributionPeriod
import net.blueshell.api.domain.contribution.persistence.ContributionReminder

/**
 * Mappings from Contribution entities to result models.
 * Used by command handlers to convert persistence entities to command results.
 */
fun Contribution.toResult(): ContributionResult = ContributionResult(
    userId = this.id.userId,
    contributionPeriodId = this.id.contributionPeriodId,
    version = this.version,
    createdAt = this.createdAt,
    updatedAt = this.updatedAt,
    deletedAt = this.deletedAt
)

fun List<Contribution>.toContributionResults(): List<ContributionResult> = this.map { it.toResult() }

fun ContributionPeriod.toResult(): ContributionPeriodResult = ContributionPeriodResult(
    id = this.id,
    startDate = this.startDate,
    endDate = this.endDate,
    halfYearFee = this.halfYearFee,
    fullYearFee = this.fullYearFee,
    alumniFee = this.alumniFee,
    listId = this.listId,
    version = this.version,
    createdAt = this.createdAt,
    updatedAt = this.updatedAt,
    deletedAt = this.deletedAt
)

fun List<ContributionPeriod>.toContributionPeriodResults(): List<ContributionPeriodResult> = this.map { it.toResult() }

fun ContributionReminder.toResult(): ContributionReminderResult = ContributionReminderResult(
    userId = this.id.userId,
    contributionPeriodId = this.id.contributionPeriodId,
    version = this.version,
    createdAt = this.createdAt,
    updatedAt = this.updatedAt,
    deletedAt = this.deletedAt
)

fun List<ContributionReminder>.toContributionReminderResults(): List<ContributionReminderResult> = this.map { it.toResult() }
