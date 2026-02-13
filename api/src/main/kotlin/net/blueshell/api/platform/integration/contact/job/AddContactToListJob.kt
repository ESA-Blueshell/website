package net.blueshell.api.platform.integration.contact.job

import com.fasterxml.jackson.databind.ObjectMapper
import net.blueshell.api.domain.contribution.application.ContributionPeriodService
import net.blueshell.api.domain.user.application.UserService
import net.blueshell.api.domain.user.application.contact.ContactData
import net.blueshell.api.domain.user.application.contact.ContactSyncAdapter
import net.blueshell.api.platform.integration.queue.AbstractJsonJobHandler
import net.blueshell.api.platform.integration.queue.ContactJobs
import net.blueshell.api.shared.enums.Role
import org.springframework.stereotype.Component

/**
 * Job handler for adding contacts to external lists.
 *
 * Uses ContactSyncAdapter (ADR-019 ACL) to isolate from specific contact provider.
 */
@Component
class AddContactToListJob(
    objectMapper: ObjectMapper,
    private val contactAdapter: ContactSyncAdapter,
    private val users: UserService,
    private val periods: ContributionPeriodService
) : AbstractJsonJobHandler<ContactJobs.AddToListPayload>(objectMapper, ContactJobs.AddToList.payloadType) {
    override val jobType: String = ContactJobs.AddToList.type

    override fun handlePayload(payload: ContactJobs.AddToListPayload) {
        val user = users.findById(payload.userId)
        val period = periods.findById(payload.periodId)

        // Ensure the list exists
        if (period.listId == null) {
            val listName = String.format(
                "Contribution Paid %d - %d",
                period.startDate.year,
                period.endDate.year
            )
            val listId = contactAdapter.createList(listName, "contributionPeriods")
            periods.updateListId(period.id!!, listId.toLong())
        }

        // Ensure the user has a contact ID
        if (user.contactId == null) {
            val contactData = ContactData(
                email = user.email,
                firstName = user.firstName,
                lastName = user.lastName,
                phoneNumber = user.phoneNumber,
                newsletter = user.newsletter,
                isMember = user.hasRole(Role.MEMBER)
            )
            val contactId = contactAdapter.syncContact(user.id!!, contactData)
            users.updateContactId(user.id!!, contactId!!.toLong())
        }

        // Add contact to list
        contactAdapter.addToList(period.listId.toString(), user.contactId.toString())
    }

}
