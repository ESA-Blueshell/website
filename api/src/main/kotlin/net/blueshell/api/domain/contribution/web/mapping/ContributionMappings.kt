package net.blueshell.api.domain.contribution.web.mapping

import net.blueshell.api.domain.contribution.command.result.ContributionPeriodResult
import net.blueshell.api.domain.contribution.command.result.ContributionReminderResult
import net.blueshell.api.domain.contribution.command.result.ContributionResult
import net.blueshell.api.domain.contribution.web.dto.response.ContributionPeriodResponse
import net.blueshell.api.domain.contribution.web.dto.response.ContributionReminderResponse
import net.blueshell.api.domain.contribution.web.dto.response.ContributionResponse
import tech.mappie.api.ObjectMappie

object ContributionResultToContributionResponseMapper : ObjectMappie<ContributionResult, ContributionResponse>()

object ContributionPeriodResultToContributionPeriodResponseMapper :
    ObjectMappie<ContributionPeriodResult, ContributionPeriodResponse>()

object ContributionReminderResultToContributionReminderResponseMapper :
    ObjectMappie<ContributionReminderResult, ContributionReminderResponse>()

fun ContributionResult.asResponse(): ContributionResponse = ContributionResultToContributionResponseMapper.map(this)

fun ContributionPeriodResult.asResponse(): ContributionPeriodResponse =
    ContributionPeriodResultToContributionPeriodResponseMapper.map(this)

fun ContributionReminderResult.asResponse(): ContributionReminderResponse =
    ContributionReminderResultToContributionReminderResponseMapper.map(this)
