package net.blueshell.api.listener.email

import net.blueshell.api.common.event.job.EventSignupEmailEvent
import net.blueshell.api.job.email.EventSignupEmailJob
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

@Component
class EventSignupEmailEventListener(
    private val job: EventSignupEmailJob
) {
    @EventListener
    fun onSend(evt: EventSignupEmailEvent) {
        val id = evt.eventSignUpId
        if (id == null) return
        job.send(id)
    }
}
