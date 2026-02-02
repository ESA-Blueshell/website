package net.blueshell.api.listener.email

import lombok.RequiredArgsConstructor
import lombok.extern.slf4j.Slf4j
import net.blueshell.api.common.event.job.EventSignupEmailEvent
import net.blueshell.api.job.email.EventSignupEmailJob
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

@Slf4j
@Component
@RequiredArgsConstructor
class EventSignupEmailEventListener {
    private val job: EventSignupEmailJob? = null

    @EventListener
    fun onSend(evt: EventSignupEmailEvent) {
        val id = evt.eventSignUpId
        if (id == null) return
        job!!.send(id)
    }
}