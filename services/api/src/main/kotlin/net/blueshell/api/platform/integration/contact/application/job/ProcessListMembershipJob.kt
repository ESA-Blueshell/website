package net.blueshell.api.platform.integration.contact.application.job

import tools.jackson.databind.ObjectMapper
import net.blueshell.api.domain.contribution.application.ContributionPeriodService
import net.blueshell.api.domain.contribution.application.ContributionService
import net.blueshell.api.platform.integration.contact.adapter.ContactListAdapter
import net.blueshell.api.platform.integration.contact.application.ContactListService
import net.blueshell.api.platform.integration.contact.application.ContributionPeriodListResolver
import net.blueshell.api.platform.integration.queue.AbstractJsonJobHandler
import net.blueshell.api.shared.job.ContactJobs
import net.blueshell.api.shared.job.SyncListMembershipCommand
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
class ProcessListMembershipJob(
    objectMapper: ObjectMapper,
    private val contactListService: ContactListService,
    private val listResolver: ContributionPeriodListResolver,
    private val periods: ContributionPeriodService,
    private val contributions: ContributionService,
    private val listAdapters: List<ContactListAdapter>,
    private val jobs: TrackedJobDispatcher,
) : AbstractJsonJobHandler<ContactJobs.ProcessListMembershipPayload>(
    objectMapper,
    ContactJobs.ProcessListMembership.payloadType
) {
    override val jobType: String = ContactJobs.ProcessListMembership.type

    override fun handlePayload(payload: ContactJobs.ProcessListMembershipPayload) {
        val period = periods.findById(payload.periodId)
        val contactList = listResolver.resolve(period)

        val hasContribution = contributions.existsByUserIdAndPeriodId(payload.userId, payload.periodId)

        if (hasContribution) {
            contactListService.createMembership(contactList.id!!, payload.userId)
            jobs.enqueue(ContactJobs.SyncContact, ContactJobs.SyncContactPayload(payload.userId))
            listAdapters.forEach { adapter ->
                jobs.enqueue(ContactJobs.SyncListMembershipToSystem, SyncListMembershipCommand(payload.userId, contactList.id!!, adapter.system))
            }
            log.debug("Queued contact sync + add-to-list for user {} in list {} (period {})", payload.userId, contactList.id, payload.periodId)
        } else {
            contactListService.deleteMembership(contactList.id!!, payload.userId)
            listAdapters.forEach { adapter ->
                jobs.enqueue(ContactJobs.SyncListMembershipToSystem, SyncListMembershipCommand(payload.userId, contactList.id!!, adapter.system))
            }
            log.debug("Queued remove-from-list for user {} in list {} (period {})", payload.userId, contactList.id, payload.periodId)
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(ProcessListMembershipJob::class.java)
    }
}
