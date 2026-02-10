package net.blueshell.api.contribution.application

import net.blueshell.api.contribution.persistence.ContributionReminder
import net.blueshell.api.contribution.persistence.ContributionReminderRepository
import net.blueshell.api.platform.integration.queue.EmailJobs
import net.blueshell.api.platform.integration.queue.JobDispatcher
import net.blueshell.api.shared.service.BaseModelService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ContributionReminderService @Autowired constructor(
    repository: ContributionReminderRepository,
    private val periodService: ContributionPeriodService,
    private val jobDispatcher: JobDispatcher
) : BaseModelService<ContributionReminder, ContributionReminder.Id, ContributionReminderRepository>(repository) {
    @Transactional(readOnly = true)
    fun findByContributionPeriodId(contributionPeriodId: Long): MutableList<ContributionReminder> {
        periodService.findById(contributionPeriodId)
        return repository.findById_ContributionPeriodId(contributionPeriodId)
    }

    fun sendReminder(reminder: ContributionReminder) {
        val reminderId = reminder.id
        jobDispatcher.enqueue(
            EmailJobs.ContributionReminder,
            EmailJobs.ContributionReminderPayload(reminderId.userId!!, reminderId.contributionPeriodId!!)
        )
    }

    fun sendReminders(reminders: MutableList<ContributionReminder>) {
        for (reminder in reminders) {
            sendReminder(reminder)
        }
    }
}
