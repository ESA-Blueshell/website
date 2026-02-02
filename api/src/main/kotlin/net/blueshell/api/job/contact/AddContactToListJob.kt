package net.blueshell.api.job.contact

import lombok.RequiredArgsConstructor
import lombok.extern.slf4j.Slf4j
import net.blueshell.api.service.ContactService
import net.blueshell.api.service.UserService
import net.blueshell.api.service.contribution.ContributionPeriodService
import org.springframework.retry.annotation.Backoff
import org.springframework.retry.annotation.Recover
import org.springframework.retry.annotation.Retryable
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClientResponseException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap

@Service
@Slf4j
@RequiredArgsConstructor
class AddContactToListJob {
    private val contacts: ContactService? = null
    private val users: UserService? = null
    private val contributionPeriods: ContributionPeriodService? = null

    @Async
    @Retryable(retryFor = [Exception::class], maxAttempts = 3, backoff = Backoff(delay = 2000, multiplier = 2))
    fun addToList(userId: Long?, periodId: Long?): CompletableFuture<Void?> {
        val jobKey = generateJobKey(userId, periodId)
        if (processingJobs.putIfAbsent(jobKey, true) != null) {
            AddContactToListJob.log.info(
                "Add-to-list job already processing for userId={} periodId={}",
                userId,
                periodId
            )
            return CompletableFuture.completedFuture<Void?>(null)
        }

        try {
            val user = users!!.findById(userId)
            val period = contributionPeriods!!.findById(periodId)

            // Ensure list exists (period listener also creates it; this is a safety net).
            if (period.getListId() == null) {
                val listId = contacts!!.createList(period)
                contributionPeriods.updateListId(periodId, listId)
                AddContactToListJob.log.info("Created list {} for periodId={}", listId, periodId)
            }

            contacts!!.addToList(period, user)
            AddContactToListJob.log.info(
                "Added user {} (id={}) to list {} for periodId={}",
                user.getEmail(), userId, period.getListId(), periodId
            )

            return CompletableFuture.completedFuture<Void?>(null)
        } catch (e: RestClientResponseException) {
            AddContactToListJob.log.error(
                "Brevo error adding userId={} to periodId={}: {}",
                userId,
                periodId,
                e.message,
                e
            )
            throw e
        } catch (e: Exception) {
            AddContactToListJob.log.error(
                "Unexpected error adding userId={} to periodId={}: {}",
                userId,
                periodId,
                e.message,
                e
            )
            throw RuntimeException("Failed to add contact to list", e)
        } finally {
            processingJobs.remove(jobKey)
        }
    }

    @Recover
    fun recover(ex: Exception, userId: Long?, periodId: Long?): CompletableFuture<Void?> {
        processingJobs.remove(generateJobKey(userId, periodId))
        AddContactToListJob.log.error("Giving up adding userId={} to periodId={}: {}", userId, periodId, ex.message, ex)
        return CompletableFuture.completedFuture<Void?>(null)
    }

    private fun generateJobKey(userId: Long?, periodId: Long?): String {
        return String.format(
            "add_contact_user_%d_to_period_%d_at_%d",
            userId, periodId, System.currentTimeMillis() / 10000
        )
    }

    companion object {
        private val processingJobs = ConcurrentHashMap<String?, Boolean?>()
    }
}
