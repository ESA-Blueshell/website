package net.blueshell.api.domain.contribution.web.mapping

import net.blueshell.api.domain.contribution.persistence.Contribution
import net.blueshell.api.domain.contribution.persistence.ContributionPeriod
import net.blueshell.api.domain.contribution.persistence.ContributionReminder
import net.blueshell.api.domain.contribution.web.dto.*
import net.blueshell.api.domain.user.persistence.User
import tech.mappie.api.ObjectMappie

object ContributionToContributionResponseMapper : ObjectMappie<Contribution, ContributionResponse>()

object ContributionPeriodToContributionPeriodResponseMapper :
    ObjectMappie<ContributionPeriod, ContributionPeriodResponse>()

object ContributionReminderToContributionReminderResponseMapper :
    ObjectMappie<ContributionReminder, ContributionReminderResponse>()

fun CreateContributionRequest.asEntity(contribution: Contribution = Contribution()): Contribution {
    contribution.user = User::class.asRef(userId!!)
    contribution.contributionPeriod = ContributionPeriod::class.asRef(contributionPeriodId!!)
    return contribution
}

fun CreateContributionPeriodRequest.asEntity(period: ContributionPeriod = ContributionPeriod()): ContributionPeriod {
    period.startDate = startDate!!
    period.endDate = endDate!!
    period.halfYearFee = halfYearFee!!
    period.fullYearFee = fullYearFee!!
    period.alumniFee = alumniFee!!
    period.listId = listId!!
    return period
}

fun UpdateContributionPeriodRequest.asEntity(period: ContributionPeriod = ContributionPeriod()): ContributionPeriod {
    period.startDate = startDate!!
    period.endDate = endDate!!
    period.halfYearFee = halfYearFee!!
    period.fullYearFee = fullYearFee!!
    period.alumniFee = alumniFee!!
    period.listId = listId!!
    version?.let { period.version = it }
    return period
}

fun CreateContributionReminderRequest.asEntity(reminder: ContributionReminder = ContributionReminder()): ContributionReminder {
    reminder.user = User::class.asRef(userId!!)
    reminder.contributionPeriod = ContributionPeriod::class.asRef(contributionPeriodId!!)
    return reminder
}

fun Contribution.asResponse(): ContributionResponse = ContributionToContributionResponseMapper.map(this)

fun ContributionPeriod.asResponse(): ContributionPeriodResponse =
    ContributionPeriodToContributionPeriodResponseMapper.map(this)

fun ContributionReminder.asResponse(): ContributionReminderResponse =
    ContributionReminderToContributionReminderResponseMapper.map(this)
