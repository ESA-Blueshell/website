package net.blueshell.api.platform.integration.contact.job

import tools.jackson.databind.ObjectMapper
import net.blueshell.api.domain.contribution.application.ContributionPeriodService
import net.blueshell.api.domain.contribution.application.ContributionService
import net.blueshell.api.platform.integration.contact.application.ContactListService
import net.blueshell.api.platform.integration.contact.application.ContactSyncService
import net.blueshell.api.platform.integration.queue.AbstractJsonJobHandler
import net.blueshell.api.shared.job.ContactJobs
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * Unified job handler for managing contact list membership.
 *
 * Handles:
 * - Creating the contribution period list if it doesn't exist (lazy via [ContactListService])
 * - Ensuring the user has a Contact (lazy via [ContactSyncService])
 * - Adding/removing the contact from the list based on contribution existence
 */
@Component
class SyncListMembershipJob(
    objectMapper: ObjectMapper,
    private val contactSyncService: ContactSyncService,
    private val contactListService: ContactListService,
    private val periods: ContributionPeriodService,
    private val contributions: ContributionService,
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
            // Ensure the user has a Contact in all active systems before adding to list
            contactSyncService.syncContact(payload.userId)
            contactListService.addContactToList(contactList.id!!, payload.userId)
            log.debug("Added user {} to list {} (period {})", payload.userId, contactList.id, payload.periodId)
        } else {
            contactListService.removeContactFromList(contactList.id!!, payload.userId)
            log.debug("Removed user {} from list {} (period {})", payload.userId, contactList.id, payload.periodId)
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(SyncListMembershipJob::class.java)
    }
}
