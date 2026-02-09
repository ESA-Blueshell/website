package net.blueshell.api.platform.integration.email.listener

import net.blueshell.api.platform.integration.event.job.EventSignupEmailEvent
import net.blueshell.api.platform.integration.email.job.EventSignupEmailJob
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

@Component
class EventSignupEmailEventListener(
    private val job: EventSignupEmailJob
) {
    @EventListener
    fun onSend(evt: EventSignupEmailEvent) {
        val id = evt.eventSignUpId ?: return
        job.send(id)
    }
}
