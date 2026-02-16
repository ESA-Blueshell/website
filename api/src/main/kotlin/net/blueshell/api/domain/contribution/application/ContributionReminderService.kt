package net.blueshell.api.domain.contribution.application

import net.blueshell.api.domain.contribution.persistence.ContributionReminder
import net.blueshell.api.domain.contribution.persistence.repository.ContributionReminderRepository
import net.blueshell.api.shared.job.EmailJobs
import net.blueshell.api.shared.job.TrackedJobDispatcher
import net.blueshell.api.shared.service.BaseModelService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ContributionReminderService @Autowired constructor(
    repository: ContributionReminderRepository,
    private val periodService: ContributionPeriodService,
    private val jobs: TrackedJobDispatcher
) : BaseModelService<ContributionReminder, ContributionReminder.Id, ContributionReminderRepository>(repository) {
    @Transactional(readOnly = true)
    fun findByContributionPeriodId(contributionPeriodId: Long): MutableList<ContributionReminder> {
        periodService.findById(contributionPeriodId)
        return repository.findByIdContributionPeriodId(contributionPeriodId)
    }

    fun sendReminder(reminder: ContributionReminder) {
        val reminderId = reminder.id
        jobs.enqueue(
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
