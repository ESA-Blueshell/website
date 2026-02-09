package net.blueshell.api.platform.integration.contact.job

import com.fasterxml.jackson.databind.ObjectMapper
import net.blueshell.api.contribution.application.ContributionPeriodService
import net.blueshell.api.platform.integration.contact.ContactService
import net.blueshell.api.platform.integration.queue.AbstractJsonJobHandler
import net.blueshell.api.user.application.UserService
import org.springframework.stereotype.Component

@Component
class AddContactToListJob(
    objectMapper: ObjectMapper,
    private val contacts: ContactService,
    private val users: UserService,
    private val periods: ContributionPeriodService
) : AbstractJsonJobHandler<AddContactToListJob.Payload>(objectMapper, Payload::class.java) {
    override val jobType: String = TYPE

    override fun handlePayload(payload: Payload) {
        val user = users.findById(payload.userId)
        val period = periods.findById(payload.periodId)

        if (period.listId == null) {
            val listId = contacts.createList(period)
            periods.updateListId(period.id!!, listId)
        }

        contacts.addToList(period, user)
    }

    companion object {
        const val TYPE = "contact.add-to-list"
    }

    data class Payload(
        val userId: Long,
        val periodId: Long
    )
}
