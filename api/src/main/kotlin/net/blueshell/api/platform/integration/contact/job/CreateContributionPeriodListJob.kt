package net.blueshell.api.platform.integration.contact.job

import net.blueshell.api.platform.integration.contact.ContactService
import net.blueshell.api.contribution.service.ContributionPeriodService
import org.slf4j.LoggerFactory
import org.springframework.retry.annotation.Backoff
import org.springframework.retry.annotation.Recover
import org.springframework.retry.annotation.Retryable
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClientResponseException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap

@Service
class CreateContributionPeriodListJob(
    private val contacts: ContactService,
    private val contributionPeriods: ContributionPeriodService
) {
    @Async
    @Retryable(retryFor = [Exception::class], maxAttempts = 3, backoff = Backoff(delay = 2000, multiplier = 2.0))
    fun createList(periodId: Long): CompletableFuture<Void?> {
        val jobKey = generateJobKey(periodId)
        if (processingJobs.putIfAbsent(jobKey, true) != null) {
            log.info("Create-list job already processing for periodId={}", periodId)
            return CompletableFuture.completedFuture<Void?>(null)
        }

        try {
            val period = contributionPeriods.findById(periodId)
            if (period.listId != null) {
                log.info(
                    "List already exists (id={}) for periodId={}",
                    period.listId,
                    periodId
                )
                return CompletableFuture.completedFuture<Void?>(null)
            }

            val listId = contacts.createList(period)
            period.listId = listId
            contributionPeriods.update(period)
            log.info("Created list {} for periodId={}", listId, periodId)

            return CompletableFuture.completedFuture<Void?>(null)
        } catch (e: RestClientResponseException) {
            log.error(
                "Brevo error creating list for periodId={}: {}",
                periodId,
                e.message,
                e
            )
            throw e
        } catch (e: Exception) {
            log.error(
                "Unexpected error creating list for periodId={}: {}",
                periodId,
                e.message,
                e
            )
            throw RuntimeException("Failed to create list", e)
        } finally {
            processingJobs.remove(jobKey)
        }
    }

    @Recover
    fun recover(ex: Exception, periodId: Long?): CompletableFuture<Void?> {
        processingJobs.remove(generateJobKey(periodId))
        log.error(
            "Giving up creating list for periodId={}: {}",
            periodId,
            ex.message,
            ex
        )
        return CompletableFuture.completedFuture<Void?>(null)
    }

    private fun generateJobKey(periodId: Long?): String {
        return String.format("create_list_for_period_%d_at_%d", periodId, System.currentTimeMillis() / 10000)
    }

    companion object {
        private val log = LoggerFactory.getLogger(CreateContributionPeriodListJob::class.java)
        private val processingJobs = ConcurrentHashMap<String, Boolean>()
    }
}
