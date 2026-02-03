package net.blueshell.api.listener.contact

import net.blueshell.api.common.event.job.AddContactToListEvent
import net.blueshell.api.job.contact.AddContactToListJob
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

@Component
class AddContactToListEventListener(
    private val job: AddContactToListJob
) {
    @EventListener
    fun onAdd(evt: AddContactToListEvent) {
        val userId = evt.userId
        val periodId = evt.periodId
        if (userId == null || periodId == null) return
        job.addToList(userId, periodId)
    }
}
