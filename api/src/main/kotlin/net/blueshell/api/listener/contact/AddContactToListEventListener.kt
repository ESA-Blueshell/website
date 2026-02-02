package net.blueshell.api.listener.contact

import lombok.RequiredArgsConstructor
import lombok.extern.slf4j.Slf4j
import net.blueshell.api.common.event.job.AddContactToListEvent
import net.blueshell.api.job.contact.AddContactToListJob
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

@Slf4j
@Component
@RequiredArgsConstructor
class AddContactToListEventListener {
    private val job: AddContactToListJob? = null

    @EventListener
    fun onAdd(evt: AddContactToListEvent) {
        val userId = evt.userId
        val periodId = evt.periodId
        if (userId == null || periodId == null) return
        job!!.addToList(userId, periodId)
    }
}