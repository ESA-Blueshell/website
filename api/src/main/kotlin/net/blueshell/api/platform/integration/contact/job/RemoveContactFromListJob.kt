package net.blueshell.api.platform.integration.contact.job

import com.fasterxml.jackson.databind.ObjectMapper
import net.blueshell.api.domain.contribution.application.ContributionPeriodService
import net.blueshell.api.domain.user.application.UserService
import net.blueshell.api.platform.integration.contact.ContactService
import net.blueshell.api.platform.integration.queue.AbstractJsonJobHandler
import net.blueshell.api.platform.integration.queue.ContactJobs
import org.springframework.stereotype.Component

@Component
class RemoveContactFromListJob(
    objectMapper: ObjectMapper,
    private val contacts: ContactService,
    private val users: UserService,
    private val periods: ContributionPeriodService
) : AbstractJsonJobHandler<ContactJobs.RemoveFromListPayload>(objectMapper, ContactJobs.RemoveFromList.payloadType) {
    override val jobType: String = ContactJobs.RemoveFromList.type

    override fun handlePayload(payload: ContactJobs.RemoveFromListPayload) {
        val user = users.findById(payload.userId)
        val period = periods.findById(payload.periodId)
        val contactId = user.contactId ?: return
        contacts.removeFromList(period, contactId)
    }

}
