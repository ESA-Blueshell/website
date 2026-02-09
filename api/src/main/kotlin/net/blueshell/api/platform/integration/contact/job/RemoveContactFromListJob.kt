package net.blueshell.api.platform.integration.contact.job

import com.fasterxml.jackson.databind.ObjectMapper
import net.blueshell.api.contribution.application.ContributionPeriodService
import net.blueshell.api.platform.integration.contact.ContactService
import net.blueshell.api.platform.integration.queue.AbstractJsonJobHandler
import net.blueshell.api.user.application.UserService
import org.springframework.stereotype.Component

@Component
class RemoveContactFromListJob(
    objectMapper: ObjectMapper,
    private val contacts: ContactService,
    private val users: UserService,
    private val periods: ContributionPeriodService
) : AbstractJsonJobHandler<RemoveContactFromListJob.Payload>(objectMapper, Payload::class.java) {
    override val jobType: String = TYPE

    override fun handlePayload(payload: Payload) {
        val user = users.findById(payload.userId)
        val period = periods.findById(payload.periodId)
        val contactId = user.contactId ?: return
        contacts.removeFromList(period, contactId)
    }

    companion object {
        const val TYPE = "contact.remove-from-list"
    }

    data class Payload(
        val userId: Long,
        val periodId: Long
    )
}
