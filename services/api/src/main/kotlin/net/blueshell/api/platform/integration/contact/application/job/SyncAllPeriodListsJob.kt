package net.blueshell.api.platform.integration.contact.application.job

import net.blueshell.api.domain.contribution.application.ContributionPeriodService
import net.blueshell.api.domain.contribution.application.ContributionService
import net.blueshell.api.domain.contribution.persistence.ContributionPeriod
import net.blueshell.api.platform.integration.contact.application.ContributionPeriodListResolver
import net.blueshell.api.platform.integration.queue.AbstractJsonJobHandler
import net.blueshell.api.shared.job.ContactJobs
import net.blueshell.api.shared.job.TrackedJobDispatcher
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper

/**
 * Nightly reconciliation of per-period contact lists: makes sure each
 * [ContributionPeriod] has a linked [net.blueshell.api.platform.integration.contact.persistence.ContactList]
 * and enqueues one [ContactJobs.ProcessListMembership] per paid contribution.
 * Scheduled before the regular contact / list syncs by [net.blueshell.api.platform.integration.contact.application.ContactSyncScheduler].
 */
@Component
class SyncAllPeriodListsJob(
    objectMapper: ObjectMapper,
    private val listResolver: ContributionPeriodListResolver,
    private val periods: ContributionPeriodService,
    private val contributions: ContributionService,
    private val jobs: TrackedJobDispatcher,
) : AbstractJsonJobHandler<ContactJobs.SyncAllPeriodListsPayload>(
    objectMapper,
    ContactJobs.SyncAllPeriodLists.payloadType,
) {
    override val jobType: String = ContactJobs.SyncAllPeriodLists.type

    override fun handlePayload(payload: ContactJobs.SyncAllPeriodListsPayload) {
        val allPeriods = periods.findAll()
        log.info("Reconciling contribution-period lists for {} periods", allPeriods.size)

        val failures = mutableListOf<Long>()
        for (period in allPeriods) {
            val periodId = period.id ?: continue
            runCatching { reconcilePeriod(period, periodId) }
                .onFailure {
                    failures += periodId
                    log.error("Failed to reconcile period {}", periodId, it)
                }
        }

        if (failures.isNotEmpty()) {
            // Other periods still processed; surface partial failure so the
            // JobExecution row reflects it and the retry/supersede flow can act.
            throw IllegalStateException("Failed to reconcile contribution periods: $failures")
        }
    }

    private fun reconcilePeriod(period: ContributionPeriod, periodId: Long) {
        listResolver.resolve(period)
        val rows = contributions.findByContributionPeriodId(periodId)
        log.debug("Period {}: enqueuing {} ProcessListMembership jobs", periodId, rows.size)
        for (contribution in rows) {
            jobs.enqueue(
                ContactJobs.ProcessListMembership,
                ContactJobs.ProcessListMembershipPayload(contribution.userId, periodId),
            )
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(SyncAllPeriodListsJob::class.java)
    }
}
