package net.blueshell.api.platform.integration.contact.application.job

import tools.jackson.databind.ObjectMapper
import net.blueshell.api.domain.contribution.application.ContributionPeriodService
import net.blueshell.api.domain.contribution.application.ContributionService
import net.blueshell.api.platform.integration.contact.application.ContactListService
import net.blueshell.api.platform.integration.queue.AbstractJsonJobHandler
import net.blueshell.api.shared.job.ContactIntegrationJobProvider
import net.blueshell.api.shared.job.ContactJobs
import net.blueshell.api.shared.job.TrackedJobDispatcher
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * Unified job handler for managing contact list membership.
 *
 * Handles:
 * - Lazy list creation via [ContactListService.findOrCreateList]
 * - Creating or removing the DB membership record
 * - Dispatching per-integration [ContactIntegrationJobProvider.listSyncJob] jobs
 *   so each external system is updated independently with retry isolation
 */
@Component
class SyncListMembershipJob(
    objectMapper: ObjectMapper,
    private val contactListService: ContactListService,
    private val periods: ContributionPeriodService,
    private val contributions: ContributionService,
    private val providers: List<ContactIntegrationJobProvider>,
    private val jobs: TrackedJobDispatcher,
) : AbstractJsonJobHandler<ContactJobs.SyncListMembershipPayload>(
    objectMapper,
    ContactJobs.SyncListMembership.payloadType
) {
    override val jobType: String = ContactJobs.SyncListMembership.type

    override fun handlePayload(payload: ContactJobs.SyncListMembershipPayload) {
        val period = periods.findById(payload.periodId)

        val listName = "Contribution Paid ${period.startDate.year} - ${period.endDate.year}"
        val contactList = if (period.contactListId == null) {
            val list = contactListService.findOrCreateList(listName, "contributionPeriods")
            periods.updateContactListId(period.id!!, list.id!!)
            list
        } else {
            contactListService.findById(period.contactListId!!)
        }

        val hasContribution = contributions.existsByUserIdAndPeriodId(payload.userId, payload.periodId)

        if (hasContribution) {
            contactListService.createMembership(contactList.id!!, payload.userId)
            providers.forEach { provider ->
                provider.contactSyncJob(payload.userId).enqueueOn(jobs)
                provider.listSyncJob(payload.userId, contactList.id!!).enqueueOn(jobs)
            }
            log.debug("Queued contact + add-to-list for user {} in list {} (period {})", payload.userId, contactList.id, payload.periodId)
        } else {
            contactListService.deleteMembership(contactList.id!!, payload.userId)
            providers.forEach { provider ->
                provider.listSyncJob(payload.userId, contactList.id!!).enqueueOn(jobs)
            }
            log.debug("Queued remove-from-list for user {} in list {} (period {})", payload.userId, contactList.id, payload.periodId)
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(SyncListMembershipJob::class.java)
    }
}
