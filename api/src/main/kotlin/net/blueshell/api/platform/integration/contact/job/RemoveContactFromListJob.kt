package net.blueshell.api.platform.integration.contact.job

import com.fasterxml.jackson.databind.ObjectMapper
import net.blueshell.api.domain.contribution.application.ContributionPeriodService
import net.blueshell.api.domain.user.application.UserService
import net.blueshell.api.domain.user.application.contact.ContactSyncAdapter
import net.blueshell.api.platform.integration.queue.AbstractJsonJobHandler
import net.blueshell.api.shared.job.ContactJobs
import org.springframework.stereotype.Component

/**
 * Job handler for removing contacts from external lists.
 *
 * Uses ContactSyncAdapter (ADR-019 ACL) to isolate from specific contact provider.
 */
@Component
class RemoveContactFromListJob(
    objectMapper: ObjectMapper,
    private val contactAdapter: ContactSyncAdapter,
    private val users: UserService,
    private val periods: ContributionPeriodService
) : AbstractJsonJobHandler<ContactJobs.RemoveFromListPayload>(objectMapper, ContactJobs.RemoveFromList.payloadType) {
    override val jobType: String = ContactJobs.RemoveFromList.type

    override fun handlePayload(payload: ContactJobs.RemoveFromListPayload) {
        val user = users.findById(payload.userId)
        val period = periods.findById(payload.periodId)
        val contactId = user.contactId ?: return
        val listId = period.listId ?: return

        // Remove contact from list
        contactAdapter.removeFromList(listId.toString(), contactId.toString())
    }

}
