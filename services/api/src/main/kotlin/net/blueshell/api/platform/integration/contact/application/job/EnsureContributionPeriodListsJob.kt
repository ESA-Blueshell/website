package net.blueshell.api.platform.integration.contact.application.job

import net.blueshell.api.domain.contribution.application.ContributionPeriodService
import net.blueshell.api.domain.contribution.application.ContributionService
import net.blueshell.api.domain.contribution.persistence.ContributionPeriod
import net.blueshell.api.platform.integration.contact.application.ContactListService
import net.blueshell.api.platform.integration.queue.AbstractJsonJobHandler
import net.blueshell.api.shared.job.ContactJobs
import net.blueshell.api.shared.job.TrackedJobDispatcher
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper

/**
 * Nightly job that reconciles the per–contribution-period contact lists.
 *
 * For every [ContributionPeriod] it makes sure the matching contact list
 * exists (creating it in every registered system on first run via
 * [ContactListService.findOrCreateList]) and links it back to the period.
 * It then enqueues one [ContactJobs.ProcessListMembership] per contribution,
 * which is the existing per-(user, period) flow — that handler is idempotent,
 * so on subsequent runs it only flips memberships that have actually
 * changed and is suppressed by the job-queue dedup when an identical job is
 * still in flight.
 *
 * Runs before the daily contact sync (02:00) and the daily list-membership
 * sync (02:30) so the lists exist and the membership rows are seeded by the
 * time those passes start.
 */
@Component
class EnsureContributionPeriodListsJob(
    objectMapper: ObjectMapper,
    private val contactListService: ContactListService,
    private val periods: ContributionPeriodService,
    private val contributions: ContributionService,
    private val jobs: TrackedJobDispatcher,
) : AbstractJsonJobHandler<ContactJobs.EnsureContributionPeriodListsPayload>(
    objectMapper,
    ContactJobs.EnsureContributionPeriodLists.payloadType,
) {
    override val jobType: String = ContactJobs.EnsureContributionPeriodLists.type

    override fun handlePayload(payload: ContactJobs.EnsureContributionPeriodListsPayload) {
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
        ensureListLinked(period)
        val rows = contributions.findByContributionPeriodId(periodId)
        log.debug("Period {}: enqueuing {} ProcessListMembership jobs", periodId, rows.size)
        for (contribution in rows) {
            jobs.enqueue(
                ContactJobs.ProcessListMembership,
                ContactJobs.ProcessListMembershipPayload(contribution.userId, periodId),
            )
        }
    }

    private fun ensureListLinked(period: ContributionPeriod) {
        if (period.contactListId != null) return
        val name = "Contribution Paid ${period.startDate.year} - ${period.endDate.year}"
        val list = contactListService.findOrCreateList(name, "contributionPeriods")
        periods.updateContactListId(period.id!!, list.id!!)
        log.info("Linked period {} to contact list {} ('{}')", period.id, list.id, name)
    }

    companion object {
        private val log = LoggerFactory.getLogger(EnsureContributionPeriodListsJob::class.java)
    }
}
