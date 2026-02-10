package net.blueshell.api.contribution.web.mapping

import net.blueshell.api.contribution.persistence.Contribution
import net.blueshell.api.contribution.persistence.ContributionPeriod
import net.blueshell.api.contribution.persistence.ContributionReminder
import net.blueshell.api.contribution.web.dto.ContributionDTO
import net.blueshell.api.contribution.web.dto.ContributionPeriodDTO
import net.blueshell.api.contribution.web.dto.ContributionReminderDTO
import org.springframework.beans.BeanUtils
import tech.mappie.api.ObjectMappie

object ContributionToContributionDTOMapper : ObjectMappie<Contribution, ContributionDTO>()

object ContributionDTOToContributionMapper : ObjectMappie<ContributionDTO, Contribution>()

object ContributionPeriodToContributionPeriodDTOMapper : ObjectMappie<ContributionPeriod, ContributionPeriodDTO>()

object ContributionPeriodDTOToContributionPeriodMapper : ObjectMappie<ContributionPeriodDTO, ContributionPeriod>()

object ContributionReminderToContributionReminderDTOMapper : ObjectMappie<ContributionReminder, ContributionReminderDTO>()

object ContributionReminderDTOToContributionReminderMapper : ObjectMappie<ContributionReminderDTO, ContributionReminder>()

fun ContributionDTO.asEntity(): Contribution = ContributionDTOToContributionMapper.map(this)

fun ContributionPeriodDTO.asEntity(existing: ContributionPeriod? = null): ContributionPeriod {
    val mapped = ContributionPeriodDTOToContributionPeriodMapper.map(this)
    if (existing == null) {
        return mapped
    }
    BeanUtils.copyProperties(mapped, existing)
    return existing
}

fun ContributionReminderDTO.asEntity(): ContributionReminder = ContributionReminderDTOToContributionReminderMapper.map(this)

fun Contribution.asDto(): ContributionDTO = ContributionToContributionDTOMapper.map(this)

fun ContributionPeriod.asDto(): ContributionPeriodDTO = ContributionPeriodToContributionPeriodDTOMapper.map(this)

fun ContributionReminder.asDto(): ContributionReminderDTO = ContributionReminderToContributionReminderDTOMapper.map(this)
