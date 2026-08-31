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
) : BaseModelService<IncassoNotification, IncassoNotification.Id, IncassoNotificationRepository>(repository) {

    @Transactional(readOnly = true)
    fun findByContributionPeriodId(contributionPeriodId: Long): MutableList<IncassoNotification> {
        periodService.findById(contributionPeriodId)
        return repository.findByIdContributionPeriodId(contributionPeriodId)
    }

    fun sendNotification(notification: IncassoNotification) {
        val id = notification.id
        jobs.runAsync(
            EmailJobs.IncassoNotification,
            EmailJobs.IncassoNotificationPayload(id.userId!!, id.contributionPeriodId!!),
        )
    }
}
