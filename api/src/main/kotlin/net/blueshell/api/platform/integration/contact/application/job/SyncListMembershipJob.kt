package net.blueshell.api.platform.integration.contact.application.job

import tools.jackson.databind.ObjectMapper
import net.blueshell.api.domain.contribution.application.ContributionPeriodService
import net.blueshell.api.domain.contribution.application.ContributionService
import net.blueshell.api.domain.user.application.contact.ContactSystemAdapter
import net.blueshell.api.platform.integration.contact.application.ContactListService
import net.blueshell.api.platform.integration.contact.application.ContactSyncService
import net.blueshell.api.platform.integration.queue.AbstractJsonJobHandler
import net.blueshell.api.shared.job.ContactJobs
import net.blueshell.api.shared.job.TrackedJobDispatcher
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * Unified job handler for managing contact list membership.
 *
 * Handles:
 * - Lazy list creation via [ContactListService.findOrCreateList]
 * - Ensuring the user's Contact record is current (via [ContactSyncService.syncContact])
 * - Creating or removing the DB membership record
 * - Dispatching per-system [ContactJobs.AddToList] or [ContactJobs.RemoveFromList] jobs
 *   so each external system is updated independently with retry isolation
 */
@Component
class SyncListMembershipJob(
    objectMapper: ObjectMapper,
    private val contactSyncService: ContactSyncService,
    private val contactListService: ContactListService,
    private val periods: ContributionPeriodService,
    private val contributions: ContributionService,
    private val listSyncAdapters: List<ContactSystemAdapter>,
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
            // Ensure Contact DB record is current (creates it if missing); dispatches SyncContactToSystem jobs
            contactSyncService.syncContact(payload.userId)
            contactListService.createMembership(contactList.id!!, payload.userId)
            listSyncAdapters.forEach { adapter ->
                jobs.enqueue(
                    ContactJobs.AddToList,
                    ContactJobs.AddToListPayload(payload.userId, contactList.id!!, adapter.system)
                )
            }
            log.debug("Queued add-to-list for user {} in list {} (period {})", payload.userId, contactList.id, payload.periodId)
        } else {
            contactListService.deleteMembership(contactList.id!!, payload.userId)
            listSyncAdapters.forEach { adapter ->
                jobs.enqueue(
                    ContactJobs.RemoveFromList,
                    ContactJobs.RemoveFromListPayload(payload.userId, contactList.id!!, adapter.system)
                )
            }
            log.debug("Queued remove-from-list for user {} in list {} (period {})", payload.userId, contactList.id, payload.periodId)
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(SyncListMembershipJob::class.java)
    }
}
