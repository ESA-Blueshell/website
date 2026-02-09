package net.blueshell.api.contribution.web.mapping

import io.mcarle.konvert.api.Konverter
import net.blueshell.api.contribution.persistence.Contribution
import net.blueshell.api.contribution.persistence.ContributionPeriod
import net.blueshell.api.contribution.persistence.ContributionReminder
import net.blueshell.api.contribution.web.dto.ContributionDTO
import net.blueshell.api.contribution.web.dto.ContributionPeriodDTO
import net.blueshell.api.contribution.web.dto.ContributionReminderDTO
import org.springframework.beans.BeanUtils

@Konverter
interface ContributionKonverter {
    fun toDTO(contribution: Contribution): ContributionDTO

    fun fromDTO(dto: ContributionDTO): Contribution
}

@Konverter
interface ContributionPeriodKonverter {
    fun toDTO(period: ContributionPeriod): ContributionPeriodDTO

    fun fromDTO(dto: ContributionPeriodDTO): ContributionPeriod
}

@Konverter
interface ContributionReminderKonverter {
    fun toDTO(reminder: ContributionReminder): ContributionReminderDTO

    fun fromDTO(dto: ContributionReminderDTO): ContributionReminder
}

private val contributionKonverter = Konverter.get<ContributionKonverter>()
private val contributionPeriodKonverter = Konverter.get<ContributionPeriodKonverter>()
private val contributionReminderKonverter = Konverter.get<ContributionReminderKonverter>()

fun ContributionDTO.asEntity(): Contribution = contributionKonverter.fromDTO(this)

fun ContributionPeriodDTO.asEntity(existing: ContributionPeriod? = null): ContributionPeriod {
    val mapped = contributionPeriodKonverter.fromDTO(this)
    if (existing == null) {
        return mapped
    }
    BeanUtils.copyProperties(mapped, existing)
    return existing
}

fun ContributionReminderDTO.asEntity(): ContributionReminder = contributionReminderKonverter.fromDTO(this)

fun Contribution.asDto(): ContributionDTO = contributionKonverter.toDTO(this)

fun ContributionPeriod.asDto(): ContributionPeriodDTO = contributionPeriodKonverter.toDTO(this)

fun ContributionReminder.asDto(): ContributionReminderDTO = contributionReminderKonverter.toDTO(this)
