package net.blueshell.api.job.contact

import net.blueshell.api.service.ContactService
import net.blueshell.api.service.UserService
import net.blueshell.api.service.contribution.ContributionPeriodService
import org.slf4j.LoggerFactory
import org.springframework.retry.annotation.Backoff
import org.springframework.retry.annotation.Recover
import org.springframework.retry.annotation.Retryable
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClientResponseException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantLock

@Service
class RemoveContactFromListJob(
    private val contacts: ContactService,
    private val users: UserService,
    private val contributionPeriods: ContributionPeriodService
) {
    @Async
    @Retryable(retryFor = [Exception::class], maxAttempts = 3, backoff = Backoff(delay = 2000, multiplier = 2))
    fun removeFromList(userId: Long?, periodId: Long?): CompletableFuture<Void?> {
        val jobKey = generateJobKey(userId, periodId)

        // Ensure uniqueness - prevent duplicate job execution
        if (processingJobs.putIfAbsent(jobKey, true) != null) {
            log.info(
                "Remove-from-list job already processing for user ID: {} and period ID: {}",
                userId,
                periodId
            )
            return CompletableFuture.completedFuture<Void?>(null)
        }

        val lock: ReentrantLock = locks.computeIfAbsent(userId) { k: Long? -> ReentrantLock() }

        try {
            lock.lock()
            log.info(
                "Starting remove-from-list job for user ID: {} and period ID: {}",
                userId,
                periodId
            )

            val user = users.findById(userId)
            val period = contributionPeriods.findById(periodId)

            contacts.removeFromList(period, user)

            log.info(
                "Successfully removed contact {} (user ID: {}) from list for period ID: {}",
                user.getEmail(), userId, periodId
            )
        } catch (e: RestClientResponseException) {
            log.error(
                "Failed to remove contact for user ID: {} and period ID: {} due to REST client error: {}",
                userId, periodId, e.message, e
            )
            throw e // Re-throw to trigger retry mechanism
        } catch (e: Exception) {
            log.error(
                "Failed to remove contact for user ID: {} and period ID: {} due to unexpected error: {}",
                userId, periodId, e.message, e
            )
            throw RuntimeException("Failed to remove contact from list", e)
        } finally {
            lock.unlock()
            processingJobs.remove(jobKey)
        }

        return CompletableFuture.completedFuture<Void?>(null)
    }

    @Recover
    fun recoverRemoveContactFromList(ex: Exception, userId: Long?, periodId: Long?): CompletableFuture<Void?> {
        val jobKey = generateJobKey(userId, periodId)
        processingJobs.remove(jobKey)
        log.error(
            "Failed to remove user with ID: {} from contribution period list with ID: {}. Error: {}",
            userId, periodId, ex.message, ex
        )
        return CompletableFuture.completedFuture<Void?>(null)
    }

    private fun generateJobKey(userId: Long?, periodId: Long?): String {
        // Bucket into ~10s windows to reduce duplicate overlap without starving retries
        return String.format(
            "remove_contact_user_%d_from_period_%d_at_%d",
            userId, periodId, System.currentTimeMillis() / 10000
        )
        // Note: the time-bucketing behavior is preserved from the original implementation.
    }

    companion object {
        private val log = LoggerFactory.getLogger(RemoveContactFromListJob::class.java)
        private val locks = ConcurrentHashMap<Long?, ReentrantLock>()
        private val processingJobs = ConcurrentHashMap<String?, Boolean?>()
    }
}
