package net.blueshell.api.contribution.domain

import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceContext
import net.blueshell.api.contribution.api.ContributionPeriodService
import net.blueshell.api.contribution.persistence.IncassoNotification
import net.blueshell.api.contribution.persistence.IncassoNotificationRepository
import net.blueshell.api.shared.job.EmailJobs
import net.blueshell.api.shared.job.JobQueue
import net.blueshell.api.shared.job.JobTrigger
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException

/** A pre-notification is recorded before it is sent, so a send failure leaves a record. */
@Service
class IncassoNotificationService(
    private val repository: IncassoNotificationRepository,
    private val periodService: ContributionPeriodService,
    private val jobs: JobQueue,
) {
    // Read back after each write, so the columns the database fills are on the answer.
    @PersistenceContext
    private lateinit var em: EntityManager

    private fun written(row: IncassoNotification): IncassoNotification = repository.saveAndFlush(row).also(em::refresh)

    @Transactional(readOnly = true)
    fun findById(id: Long): IncassoNotification =
        repository.findById(id).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "IncassoNotification not found with id: $id")
        }

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
        val saved = written(notification)
        jobs.runAsync(
            EmailJobs.IncassoNotification,
            EmailJobs.IncassoNotificationPayload(requireNotNull(saved.id)),
            JobTrigger.SITE_ACTION,
        )
        return saved
    }
}
