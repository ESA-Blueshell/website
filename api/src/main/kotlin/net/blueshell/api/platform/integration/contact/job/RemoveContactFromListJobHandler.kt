package net.blueshell.api.platform.integration.contact.job

import com.fasterxml.jackson.databind.ObjectMapper
import net.blueshell.api.contribution.application.ContributionPeriodService
import net.blueshell.api.platform.integration.contact.ContactService
import net.blueshell.api.platform.integration.queue.AbstractJsonJobHandler
import net.blueshell.api.user.application.UserService
import org.springframework.stereotype.Component

@Component
class RemoveContactFromListJobHandler(
    objectMapper: ObjectMapper,
    private val contacts: ContactService,
    private val users: UserService,
    private val periods: ContributionPeriodService
) : AbstractJsonJobHandler<RemoveContactFromListPayload>(objectMapper, RemoveContactFromListPayload::class.java) {
    override val jobType: String = JOB_TYPE

    override fun handlePayload(payload: RemoveContactFromListPayload) {
        val user = users.findById(payload.userId)
        val period = periods.findById(payload.periodId)
        val contactId = user.contactId ?: return
        contacts.removeFromList(period, contactId)
    }

    companion object {
        const val JOB_TYPE = "contact.remove-from-list"
    }
}
