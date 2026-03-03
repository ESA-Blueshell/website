package net.blueshell.api.platform.integration.contact.job

import tools.jackson.databind.ObjectMapper
import net.blueshell.api.domain.contribution.application.ContributionPeriodService
import net.blueshell.api.domain.contribution.application.ContributionService
import net.blueshell.api.domain.user.application.UserService
import net.blueshell.api.domain.user.application.contact.ContactSyncAdapter
import net.blueshell.api.domain.user.application.contact.toContactData
import net.blueshell.api.platform.integration.queue.AbstractJsonJobHandler
import net.blueshell.api.shared.job.ContactJobs
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * Unified job handler for managing contact list membership.
 *
 * Handles:
 * - Creating the contribution period list if it doesn't exist
 * - Syncing the user's contact if needed
 * - Adding/removing the contact from the list based on contribution existence
 */
@Component
class SyncListMembershipJob(
    objectMapper: ObjectMapper,
    private val contactAdapter: ContactSyncAdapter,
    private val users: UserService,
    private val periods: ContributionPeriodService,
    private val contributions: ContributionService
) : AbstractJsonJobHandler<ContactJobs.SyncListMembershipPayload>(
    objectMapper,
    ContactJobs.SyncListMembership.payloadType
) {
    override val jobType: String = ContactJobs.SyncListMembership.type

    override fun handlePayload(payload: ContactJobs.SyncListMembershipPayload) {
        val user = users.findById(payload.userId)
        val period = periods.findById(payload.periodId)

        // Ensure the period list exists (create lazily if needed)
        val listId = if (period.listId == null) {
            val listName = String.format(
                "Contribution Paid %d - %d",
                period.startDate.year,
                period.endDate.year
            )
            val newListId = contactAdapter.createList(listName, "contributionPeriods")
            periods.updateListId(period.id!!, newListId.toLong())
            newListId
        } else {
            period.listId.toString()
        }

        // Check if user has an active contribution for this period
        val hasContribution = contributions.existsByUserIdAndPeriodId(payload.userId, payload.periodId)

        if (hasContribution) {
            // Ensure user has a contactId (sync contact if null)
            val contactId = if (user.contactId == null) {
                val contactData = user.toContactData()
                val syncedContactId = contactAdapter.syncContact(user.id!!, contactData)
                users.updateContactLink(user, syncedContactId.toLong())
                syncedContactId
            } else {
                user.contactId.toString()
            }

            contactAdapter.addToList(listId, contactId)
            log.debug("Added contact {} to list {} for user {} period {}", contactId, listId, payload.userId, payload.periodId)
        } else {
            // Remove from list if user has a contactId
            if (user.contactId != null) {
                contactAdapter.removeFromList(listId, user.contactId.toString())
                log.debug("Removed contact {} from list {} for user {} period {}", user.contactId, listId, payload.userId, payload.periodId)
            }
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(SyncListMembershipJob::class.java)
    }
}
