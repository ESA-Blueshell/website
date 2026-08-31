package net.blueshell.api.contribution.web

import net.blueshell.api.contribution.domain.ContributionPeriodResult
import net.blueshell.api.contribution.domain.ContributionReminderResult
import net.blueshell.api.contribution.domain.ContributionResult

fun ContributionResult.asResponse(): ContributionResponse =
    ContributionResponse(
        userId = this.userId,
        contributionPeriodId = this.contributionPeriodId,
        version = this.version,
        createdAt = this.createdAt,
        updatedAt = this.updatedAt,
    )

fun ContributionPeriodResult.asResponse(): ContributionPeriodResponse =
    ContributionPeriodResponse(
        id = this.id,
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

fun ContributionReminderResult.asResponse(): ContributionReminderResponse =
    ContributionReminderResponse(
        id = this.id,
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
