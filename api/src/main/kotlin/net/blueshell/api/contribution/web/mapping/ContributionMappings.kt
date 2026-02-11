package net.blueshell.api.contribution.web.mapping

import net.blueshell.api.contribution.persistence.Contribution
import net.blueshell.api.contribution.persistence.ContributionPeriod
import net.blueshell.api.contribution.persistence.ContributionReminder
import net.blueshell.api.contribution.web.dto.ContributionDTO
import net.blueshell.api.contribution.web.dto.ContributionPeriodDTO
import net.blueshell.api.contribution.web.dto.ContributionReminderDTO
import tech.mappie.api.ObjectMappie

object ContributionToContributionDTOMapper : ObjectMappie<Contribution, ContributionDTO>()

object ContributionPeriodToContributionPeriodDTOMapper : ObjectMappie<ContributionPeriod, ContributionPeriodDTO>()

object ContributionReminderToContributionReminderDTOMapper :
    ObjectMappie<ContributionReminder, ContributionReminderDTO>()

fun ContributionDTO.asEntity(contribution: Contribution = Contribution()): Contribution {
    contribution.userId = userId!!
    contribution.contributionPeriodId = contributionPeriodId!!
    version?.let { contribution.version = it }
    return contribution
}

fun ContributionPeriodDTO.asEntity(period: ContributionPeriod = ContributionPeriod()): ContributionPeriod {
    period.startDate = startDate!!
    period.endDate = endDate!!
    period.halfYearFee = halfYearFee!!
    period.fullYearFee = fullYearFee!!
    period.alumniFee = alumniFee!!
    period.listId = listId!!
    version?.let { period.version = it }
    return period
}

fun ContributionReminderDTO.asEntity(reminder: ContributionReminder = ContributionReminder()): ContributionReminder {
    reminder.userId = userId!!
    reminder.contributionPeriodId = contributionPeriodId!!
    version?.let { reminder.version = it }
    return reminder
}

fun Contribution.asDto(): ContributionDTO = ContributionToContributionDTOMapper.map(this)

fun ContributionPeriod.asDto(): ContributionPeriodDTO = ContributionPeriodToContributionPeriodDTOMapper.map(this)

fun ContributionReminder.asDto(): ContributionReminderDTO =
    ContributionReminderToContributionReminderDTOMapper.map(this)
