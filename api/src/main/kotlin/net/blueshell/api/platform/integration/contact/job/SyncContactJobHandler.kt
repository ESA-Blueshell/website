package net.blueshell.api.platform.integration.contact.job

import com.fasterxml.jackson.databind.ObjectMapper
import net.blueshell.api.platform.integration.contact.ContactService
import net.blueshell.api.platform.integration.queue.AbstractJsonJobHandler
import net.blueshell.api.user.application.UserService
import org.springframework.stereotype.Component

@Component
class SyncContactJobHandler(
    objectMapper: ObjectMapper,
    private val contacts: ContactService,
    private val users: UserService
) : AbstractJsonJobHandler<SyncContactPayload>(objectMapper, SyncContactPayload::class.java) {
    override val jobType: String = JOB_TYPE

    override fun handlePayload(payload: SyncContactPayload) {
        val user = users.findById(payload.userId)
        contacts.sync(user)
    }

    companion object {
        const val JOB_TYPE = "contact.sync"
    }
}
