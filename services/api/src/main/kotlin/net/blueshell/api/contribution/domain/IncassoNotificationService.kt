package net.blueshell.api.contribution.domain

import net.blueshell.api.contribution.api.ContributionPeriodService
import net.blueshell.api.contribution.persistence.IncassoNotification
import net.blueshell.api.contribution.persistence.IncassoNotificationRepository
import net.blueshell.api.shared.job.EmailJobs
import net.blueshell.api.shared.job.JobQueue
import net.blueshell.api.shared.service.BaseModelService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/** A pre-notification is recorded before it is sent, so a send failure leaves a record. */
@Service
class IncassoNotificationService(
    repository: IncassoNotificationRepository,
    private val periodService: ContributionPeriodService,
    private val jobs: JobQueue,
) : BaseModelService<IncassoNotification, Long, IncassoNotificationRepository>(repository) {

    @Transactional(readOnly = true)
    fun findByContributionPeriodId(contributionPeriodId: Long): MutableList<IncassoNotification> {
        periodService.findById(contributionPeriodId)
        return repository.findByContributionPeriod_Id(contributionPeriodId)
    }

    /**
     * Writes the pre-notification and queues its email, in that order and in one transaction.
     * The job carries the notification's own id, so it sends the one it wrote, and the
     * dispatcher holds the send until this transaction commits.
     */
    @Transactional
    fun record(notification: IncassoNotification): IncassoNotification {
        val written = create(notification)
        jobs.runAsync(
            EmailJobs.IncassoNotification,
            EmailJobs.IncassoNotificationPayload(requireNotNull(written.id)),
        )
        return written
    }
}
