package net.blueshell.api.domain.contribution.web.mapping.response

import net.blueshell.api.domain.contribution.application.result.ContributionPeriodResult
import net.blueshell.api.domain.contribution.application.result.ContributionReminderResult
import net.blueshell.api.domain.contribution.application.result.ContributionResult
import net.blueshell.api.domain.contribution.web.dto.response.ContributionPeriodResponse
import net.blueshell.api.domain.contribution.web.dto.response.ContributionReminderResponse
import net.blueshell.api.domain.contribution.web.dto.response.ContributionResponse

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
        userId = this.userId,
        contributionPeriodId = this.contributionPeriodId,
        version = this.version,
        createdAt = this.createdAt,
        updatedAt = this.updatedAt,
    )
