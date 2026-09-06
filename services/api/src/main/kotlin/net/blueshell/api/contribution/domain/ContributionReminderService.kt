package net.blueshell.api.contribution.domain

import net.blueshell.api.contribution.persistence.ContributionReminder
import net.blueshell.api.contribution.persistence.ContributionReminderRepository
import net.blueshell.api.shared.job.EmailJobs
import net.blueshell.api.shared.job.JobQueue
import net.blueshell.api.shared.service.BaseModelService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import net.blueshell.api.contribution.api.ContributionPeriodService

@Service
class ContributionReminderService @Autowired constructor(
    repository: ContributionReminderRepository,
    private val periodService: ContributionPeriodService,
    private val jobs: JobQueue
) : BaseModelService<ContributionReminder, Long, ContributionReminderRepository>(repository) {
    @Transactional(readOnly = true)
    fun findByContributionPeriodId(contributionPeriodId: Long): MutableList<ContributionReminder> {
        periodService.findById(contributionPeriodId)
        return repository.findByContributionPeriod_Id(contributionPeriodId)
    }

    /**
     * Writes the ask and queues its email, in that order and in one transaction. The job
     * carries the ask's own id, so a repeat ask sends the email it wrote rather than an
     * earlier one, and the dispatcher holds the send until this transaction commits.
     */
    @Transactional
    fun record(reminder: ContributionReminder): ContributionReminder {
        val written = create(reminder)
        jobs.runAsync(
            EmailJobs.ContributionReminder,
            EmailJobs.ContributionReminderPayload(requireNotNull(written.id)),
        )
        return written
    }

    @Transactional
    fun recordAll(reminders: List<ContributionReminder>): List<ContributionReminder> = reminders.map { record(it) }
}
