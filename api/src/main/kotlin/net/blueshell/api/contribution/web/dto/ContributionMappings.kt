package net.blueshell.api.contribution.web.dto

import io.mcarle.konvert.api.Konverter
import net.blueshell.api.contribution.persistence.Contribution
import net.blueshell.api.contribution.persistence.ContributionPeriod
import net.blueshell.api.contribution.persistence.ContributionReminder

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

fun ContributionDTO.asEntity(contribution: Contribution = Contribution()): Contribution {
    val mapped = contributionKonverter.fromDTO(this)
    contribution.userId = mapped.userId
    contribution.contributionPeriodId = mapped.contributionPeriodId
    version?.let { contribution.version = it }
    return contribution
}

fun ContributionPeriodDTO.asEntity(period: ContributionPeriod = ContributionPeriod()): ContributionPeriod {
    val mapped = contributionPeriodKonverter.fromDTO(this)
    period.startDate = mapped.startDate
    period.endDate = mapped.endDate
    period.halfYearFee = mapped.halfYearFee
    period.fullYearFee = mapped.fullYearFee
    period.alumniFee = mapped.alumniFee
    period.listId = mapped.listId
    version?.let { period.version = it }
    return period
}

fun ContributionReminderDTO.asEntity(reminder: ContributionReminder = ContributionReminder()): ContributionReminder {
    val mapped = contributionReminderKonverter.fromDTO(this)
    reminder.userId = mapped.userId
    reminder.contributionPeriodId = mapped.contributionPeriodId
    version?.let { reminder.version = it }
    return reminder
}
