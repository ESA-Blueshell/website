package net.blueshell.api.domain.contribution.web.mapping

import net.blueshell.api.domain.contribution.persistence.Contribution
import net.blueshell.api.domain.contribution.persistence.ContributionPeriod
import net.blueshell.api.domain.contribution.persistence.ContributionReminder
import net.blueshell.api.domain.contribution.web.dto.ContributionPeriodResponse
import net.blueshell.api.domain.contribution.web.dto.ContributionReminderResponse
import net.blueshell.api.domain.contribution.web.dto.ContributionResponse
import tech.mappie.api.ObjectMappie

object ContributionToContributionResponseMapper : ObjectMappie<Contribution, ContributionResponse>()

object ContributionPeriodToContributionPeriodResponseMapper :
    ObjectMappie<ContributionPeriod, ContributionPeriodResponse>()

object ContributionReminderToContributionReminderResponseMapper :
    ObjectMappie<ContributionReminder, ContributionReminderResponse>()

fun Contribution.asResponse(): ContributionResponse = ContributionToContributionResponseMapper.map(this)

fun ContributionPeriod.asResponse(): ContributionPeriodResponse =
    ContributionPeriodToContributionPeriodResponseMapper.map(this)

fun ContributionReminder.asResponse(): ContributionReminderResponse =
    ContributionReminderToContributionReminderResponseMapper.map(this)
