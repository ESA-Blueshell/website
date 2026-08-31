package net.blueshell.api.contribution.domain

import net.blueshell.api.contribution.persistence.ContributionReminder
import net.blueshell.api.contribution.persistence.ContributionReminderRepository
import net.blueshell.api.shared.job.EmailJobs
import net.blueshell.api.shared.job.TrackedJobDispatcher
import net.blueshell.api.shared.service.BaseModelService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import net.blueshell.api.contribution.api.ContributionPeriodService

@Service
class ContributionReminderService @Autowired constructor(
    repository: ContributionReminderRepository,
    private val periodService: ContributionPeriodService,
    private val jobs: TrackedJobDispatcher
) : BaseModelService<ContributionReminder, Long, ContributionReminderRepository>(repository) {
    @Transactional(readOnly = true)
    fun findByContributionPeriodId(contributionPeriodId: Long): MutableList<ContributionReminder> {
        periodService.findById(contributionPeriodId)
        return repository.findByContributionPeriod_Id(contributionPeriodId)
    }

    /** The job carries the ask's own id, so a repeat ask sends the email it wrote, not an earlier one. */
    fun sendReminder(reminder: ContributionReminder) {
        jobs.runAsync(
            EmailJobs.ContributionReminder,
            EmailJobs.ContributionReminderPayload(requireNotNull(reminder.id)),
        )
    }

    fun sendReminders(reminders: MutableList<ContributionReminder>) {
        for (reminder in reminders) {
            sendReminder(reminder)
        }
    }
}
