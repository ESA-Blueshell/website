package net.blueshell.api.service.contribution

import net.blueshell.api.base.BaseModelService
import net.blueshell.api.common.event.job.ContributionReminderEmailEvent
import net.blueshell.api.model.contribution.ContributionReminder
import net.blueshell.api.repository.contribution.ContributionReminderRepository
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ContributionReminderService @Autowired constructor(
    repository: ContributionReminderRepository,
    private val periodService: ContributionPeriodService,
    private val eventPublisher: ApplicationEventPublisher
) : BaseModelService<ContributionReminder, Long, ContributionReminderRepository>(repository) {
    @Transactional(readOnly = true)
    fun findByContributionPeriodId(contributionPeriodId: Long): MutableList<ContributionReminder> {
        periodService.findById(contributionPeriodId)
        return repository!!.findByContributionPeriodId(contributionPeriodId)
    }

    fun sendReminder(reminder: ContributionReminder) {
        eventPublisher.publishEvent(ContributionReminderEmailEvent(reminder.id))
    }

    fun sendReminders(reminders: MutableList<ContributionReminder>) {
        for (reminder in reminders) {
            sendReminder(reminder)
        }
    }
}
