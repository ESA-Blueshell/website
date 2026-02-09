package net.blueshell.api.platform.integration.contact.job

import com.fasterxml.jackson.databind.ObjectMapper
import net.blueshell.api.contribution.application.ContributionPeriodService
import net.blueshell.api.platform.integration.contact.ContactService
import net.blueshell.api.platform.integration.queue.AbstractJsonJobHandler
import net.blueshell.api.user.application.UserService
import org.springframework.stereotype.Component

@Component
class AddContactToListJobHandler(
    objectMapper: ObjectMapper,
    private val contacts: ContactService,
    private val users: UserService,
    private val periods: ContributionPeriodService
) : AbstractJsonJobHandler<AddContactToListPayload>(objectMapper, AddContactToListPayload::class.java) {
    override val jobType: String = JOB_TYPE

    override fun handlePayload(payload: AddContactToListPayload) {
        val user = users.findById(payload.userId)
        val period = periods.findById(payload.periodId)

        if (period.listId == null) {
            val listId = contacts.createList(period)
            periods.updateListId(period.id!!, listId)
        }

        contacts.addToList(period, user)
    }

    companion object {
        const val JOB_TYPE = "contact.add-to-list"
    }
}
