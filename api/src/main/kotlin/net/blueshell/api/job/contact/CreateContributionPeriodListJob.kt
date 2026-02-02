package net.blueshell.api.job.contact

import lombok.RequiredArgsConstructor
import lombok.extern.slf4j.Slf4j
import net.blueshell.api.service.ContactService
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
class CreateContributionPeriodListJob {
    private val contacts: ContactService? = null
    private val contributionPeriods: ContributionPeriodService? = null

    @Async
    @Retryable(retryFor = [Exception::class], maxAttempts = 3, backoff = Backoff(delay = 2000, multiplier = 2))
    fun createList(periodId: Long?): CompletableFuture<Void?> {
        val jobKey = generateJobKey(periodId)
        if (processingJobs.putIfAbsent(jobKey, true) != null) {
            CreateContributionPeriodListJob.log.info("Create-list job already processing for periodId={}", periodId)
            return CompletableFuture.completedFuture<Void?>(null)
        }

        try {
            val period = contributionPeriods!!.findById(periodId)
            if (period.getListId() != null) {
                CreateContributionPeriodListJob.log.info(
                    "List already exists (id={}) for periodId={}",
                    period.getListId(),
                    periodId
                )
                return CompletableFuture.completedFuture<Void?>(null)
            }

            val listId = contacts!!.createList(period)
            period.setListId(listId)
            contributionPeriods.update(period)
            CreateContributionPeriodListJob.log.info("Created list {} for periodId={}", listId, periodId)

            return CompletableFuture.completedFuture<Void?>(null)
        } catch (e: RestClientResponseException) {
            CreateContributionPeriodListJob.log.error(
                "Brevo error creating list for periodId={}: {}",
                periodId,
                e.message,
                e
            )
            throw e
        } catch (e: Exception) {
            CreateContributionPeriodListJob.log.error(
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
        CreateContributionPeriodListJob.log.error(
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
        private val processingJobs = ConcurrentHashMap<String?, Boolean?>()
    }
}
