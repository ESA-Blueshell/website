package net.blueshell.api.contribution.web

import net.blueshell.api.contribution.persistence.Contribution
import net.blueshell.api.contribution.persistence.ContributionPeriod
import net.blueshell.api.contribution.persistence.ContributionReminder

/** ContributionResponse.remindedAt has no entity behind it, so it stays at its default. */
fun Contribution.asResponse(): ContributionResponse =
    ContributionResponse(
        userId = this.userId,
        contributionPeriodId = this.contributionPeriodId,
        version = this.version,
        createdAt = this.createdAt,
        updatedAt = this.updatedAt,
    )

fun ContributionPeriod.asResponse(): ContributionPeriodResponse =
    ContributionPeriodResponse(
        id = this.id!!,
        startDate = this.startDate,
        endDate = this.endDate,
        halfYearCutoffDate = this.halfYearCutoffDate,
        halfYearFee = this.halfYearFee,
        fullYearFee = this.fullYearFee,
        alumniFee = this.alumniFee,
        contactListId = this.contactListId,
        version = this.version,
        createdAt = this.createdAt,
        updatedAt = this.updatedAt,
    )

fun ContributionReminder.asResponse(): ContributionReminderResponse =
    ContributionReminderResponse(
        id = this.id!!,
        userId = this.userId,
        contributionPeriodId = this.contributionPeriodId,
        askedAt = this.askedAt,
        feeType = this.feeType,
        amount = this.amount,
        paymentDueDate = this.paymentDueDate,
        version = this.version,
        createdAt = this.createdAt,
        updatedAt = this.updatedAt,
    )
