package net.blueshell.api.platform.integration.contact.listener

import net.blueshell.api.platform.integration.event.job.AddContactToListEvent
import net.blueshell.api.platform.integration.contact.job.AddContactToListJob
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
