package net.blueshell.api.contribution.web.mapper

import io.mcarle.konvert.api.Konverter
import net.blueshell.api.contribution.web.dto.ContributionReminderDTO
import net.blueshell.api.shared.mapper.BaseMapper
import net.blueshell.api.contribution.persistence.ContributionReminder
import org.springframework.stereotype.Component

@Konverter
interface ContributionReminderKonverter {
    fun toDTO(reminder: ContributionReminder): ContributionReminderDTO

    fun fromDTO(dto: ContributionReminderDTO): ContributionReminder
}

@Component
class ContributionReminderMapper : BaseMapper<ContributionReminder, ContributionReminderDTO>() {
    private val konverter = konverter<ContributionReminderKonverter>()

    override fun fromDTO(dto: ContributionReminderDTO): ContributionReminder = konverter.fromDTO(dto)

    fun fromDTO(dto: ContributionReminderDTO, reminder: ContributionReminder): ContributionReminder {
        val mapped = konverter.fromDTO(dto)
        reminder.userId = mapped.userId
        reminder.contributionPeriodId = mapped.contributionPeriodId
        dto.version?.let { reminder.version = it }
        return reminder
    }

    override fun toDTO(reminder: ContributionReminder): ContributionReminderDTO = konverter.toDTO(reminder)
}

fun ContributionReminder.asDTO(mapper: ContributionReminderMapper): ContributionReminderDTO = mapper.toDTO(this)

fun ContributionReminderDTO.asEntity(mapper: ContributionReminderMapper): ContributionReminder = mapper.fromDTO(this)
