package net.blueshell.api.platform.integration.contact.job

import net.blueshell.api.platform.integration.contact.ContactService
import net.blueshell.api.user.service.UserService
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
class SyncContactJob(
    private val contacts: ContactService,
    private val users: UserService
) {
    @Async
    @Retryable(retryFor = [Exception::class], maxAttempts = 3, backoff = Backoff(delay = 2000, multiplier = 2.0))
    fun sync(userId: Long): CompletableFuture<Void?> {
        val jobKey = generateJobKey(userId)

        // Ensure uniqueness - prevent duplicate job execution
        if (processingJobs.putIfAbsent(jobKey, true) != null) {
            log.info("Contact sync job already processing for user ID: {}", userId)
            return CompletableFuture.completedFuture<Void?>(null)
        }

        val lock: ReentrantLock = locks.computeIfAbsent(userId) { k: Long? -> ReentrantLock() }

        try {
            lock.lock()
            log.info("Processing contact sync job for user ID: {}", userId)

            val user = users.findById(userId)

            // Perform the contact synchronization
            contacts.sync(user)

            log.info("Successfully synchronized contact for user: {} (ID: {})", user.email, userId)
        } catch (e: RestClientResponseException) {
            log.error(
                "Failed to sync contact for user ID: {} due to REST client error: {}",
                userId,
                e.message,
                e
            )
            throw e // Re-throw to trigger retry mechanism
        } catch (e: Exception) {
            log.error(
                "Failed to sync contact for user ID: {} due to unexpected error: {}",
                userId,
                e.message,
                e
            )
            throw RuntimeException("Failed to sync contact", e)
        } finally {
            lock.unlock()
            processingJobs.remove(jobKey)
        }

        return CompletableFuture.completedFuture<Void?>(null)
    }

    @Recover
    fun recoverSyncContact(ex: Exception, userId: Long?): CompletableFuture<Void?> {
        val jobKey = generateJobKey(userId)
        processingJobs.remove(jobKey)
        log.error(
            "Failed to sync contact after retries for user ID: {}. Error: {}",
            userId,
            ex.message,
            ex
        )
        return CompletableFuture.completedFuture<Void?>(null)
    }

    private fun generateJobKey(userId: Long?): String {
        return String.format("contact_sync_%d_%d", userId, System.currentTimeMillis() / 10000)
    }

    companion object {
        private val log = LoggerFactory.getLogger(SyncContactJob::class.java)
        private val locks = ConcurrentHashMap<Long, ReentrantLock>()
        private val processingJobs = ConcurrentHashMap<String, Boolean>()
    }
}
