package net.blueshell.api.platform.integration.contact.job

import com.fasterxml.jackson.databind.ObjectMapper
import net.blueshell.api.domain.contribution.application.ContributionPeriodService
import net.blueshell.api.platform.integration.contact.ContactService
import net.blueshell.api.platform.integration.queue.AbstractJsonJobHandler
import net.blueshell.api.platform.integration.queue.ContactJobs
import net.blueshell.api.domain.user.application.UserService
import org.springframework.stereotype.Component

@Component
class AddContactToListJob(
    objectMapper: ObjectMapper,
    private val contacts: ContactService,
    private val users: UserService,
    private val periods: ContributionPeriodService
) : AbstractJsonJobHandler<ContactJobs.AddToListPayload>(objectMapper, ContactJobs.AddToList.payloadType) {
    override val jobType: String = ContactJobs.AddToList.type

    override fun handlePayload(payload: ContactJobs.AddToListPayload) {
        val user = users.findById(payload.userId)
        val period = periods.findById(payload.periodId)

        if (period.listId == null) {
            val listId = contacts.createList(period)
            periods.updateListId(period.id!!, listId)
        }

        contacts.addToList(period, user)
    }

}
