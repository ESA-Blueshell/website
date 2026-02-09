package net.blueshell.api.platform.integration.contact.job

import com.fasterxml.jackson.databind.ObjectMapper
import net.blueshell.api.platform.integration.contact.ContactService
import net.blueshell.api.platform.integration.queue.AbstractJsonJobHandler
import net.blueshell.api.user.application.UserService
import org.springframework.stereotype.Component

@Component
class SyncContactJob(
    objectMapper: ObjectMapper,
    private val contacts: ContactService,
    private val users: UserService
) : AbstractJsonJobHandler<SyncContactJob.Payload>(objectMapper, Payload::class.java) {
    override val jobType: String = TYPE

    override fun handlePayload(payload: Payload) {
        val user = users.findById(payload.userId)
        contacts.sync(user)
    }

    companion object {
        const val TYPE = "contact.sync"
    }

    data class Payload(val userId: Long)
}
