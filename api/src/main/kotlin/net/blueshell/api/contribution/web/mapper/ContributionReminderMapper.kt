package net.blueshell.api.contribution.web.mapper

import net.blueshell.api.contribution.web.dto.ContributionReminderDTO
import net.blueshell.api.shared.mapper.BaseMapper
import net.blueshell.api.contribution.persistence.ContributionReminder
import org.springframework.stereotype.Component

@Component
class ContributionReminderMapper : BaseMapper<ContributionReminder, ContributionReminderDTO>() {
    override fun fromDTO(dto: ContributionReminderDTO): ContributionReminder = fromDTO(dto, ContributionReminder())

    fun fromDTO(dto: ContributionReminderDTO, reminder: ContributionReminder): ContributionReminder {
        reminder.userId = requireNotNull(dto.userId)
        reminder.contributionPeriodId = requireNotNull(dto.contributionPeriodId)
        dto.version?.let { reminder.version = it }
        return reminder
    }

    override fun toDTO(reminder: ContributionReminder): ContributionReminderDTO {
        return ContributionReminderDTO(
            userId = reminder.userId,
            contributionPeriodId = reminder.contributionPeriodId
        ).also { dto ->
            dto.version = reminder.version
        }
    }
}

fun ContributionReminder.asDTO(mapper: ContributionReminderMapper): ContributionReminderDTO = mapper.toDTO(this)

fun ContributionReminderDTO.asEntity(mapper: ContributionReminderMapper): ContributionReminder = mapper.fromDTO(this)
