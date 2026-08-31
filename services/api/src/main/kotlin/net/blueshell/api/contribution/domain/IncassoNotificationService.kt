package net.blueshell.api.contribution.domain

import net.blueshell.api.contribution.api.ContributionPeriodService
import net.blueshell.api.contribution.persistence.IncassoNotification
import net.blueshell.api.contribution.persistence.IncassoNotificationRepository
import net.blueshell.api.shared.job.EmailJobs
import net.blueshell.api.shared.job.TrackedJobDispatcher
import net.blueshell.api.shared.service.BaseModelService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/** A pre-notification is recorded before it is sent, so a send failure leaves a record. */
@Service
class IncassoNotificationService(
    repository: IncassoNotificationRepository,
    private val periodService: ContributionPeriodService,
    private val jobs: TrackedJobDispatcher,
) : BaseModelService<IncassoNotification, Long, IncassoNotificationRepository>(repository) {

    @Transactional(readOnly = true)
    fun findByContributionPeriodId(contributionPeriodId: Long): MutableList<IncassoNotification> {
        periodService.findById(contributionPeriodId)
        return repository.findByContributionPeriod_Id(contributionPeriodId)
    }

    /** The job carries the notification's own id, so it sends the one it wrote. */
    fun sendNotification(notification: IncassoNotification) {
        jobs.runAsync(
            EmailJobs.IncassoNotification,
            EmailJobs.IncassoNotificationPayload(requireNotNull(notification.id)),
        )
    }
}
