package net.blueshell.api.domain.contribution.application

import net.blueshell.api.domain.contribution.persistence.IncassoNotification
import net.blueshell.api.domain.contribution.persistence.repository.IncassoNotificationRepository
import net.blueshell.api.shared.job.EmailJobs
import net.blueshell.api.shared.job.TrackedJobDispatcher
import net.blueshell.api.shared.service.BaseModelService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class IncassoNotificationService @Autowired constructor(
    repository: IncassoNotificationRepository,
    private val periodService: ContributionPeriodService,
    private val jobs: TrackedJobDispatcher
) : BaseModelService<IncassoNotification, IncassoNotification.Id, IncassoNotificationRepository>(repository) {
    @Transactional(readOnly = true)
    fun findByContributionPeriodId(contributionPeriodId: Long): MutableList<IncassoNotification> {
        periodService.findById(contributionPeriodId)
        return repository.findByIdContributionPeriodId(contributionPeriodId)
    }

    @Transactional(readOnly = true)
    fun findLastNotificationForUserAndPeriod(userId: Long, contributionPeriodId: Long): IncassoNotification? {
        return repository.findByIdContributionPeriodId(contributionPeriodId)
            .firstOrNull { it.userId == userId }
    }

    fun sendNotification(notification: IncassoNotification) {
        val notificationId = notification.id
        jobs.enqueue(
            EmailJobs.IncassoNotification,
            EmailJobs.IncassoNotificationPayload(notificationId.userId!!, notificationId.contributionPeriodId!!)
        )
    }

    fun sendNotifications(notifications: MutableList<IncassoNotification>) {
        for (notification in notifications) {
            sendNotification(notification)
        }
    }
}
