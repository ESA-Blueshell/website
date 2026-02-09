package net.blueshell.api.platform.integration.contact.listener

import net.blueshell.api.platform.integration.event.job.RemoveContactFromListEvent
import net.blueshell.api.platform.integration.contact.job.RemoveContactFromListJob
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

@Component
class RemoveContactFromListEventListener(
    private val job: RemoveContactFromListJob
) {
    @EventListener
    fun onRemove(evt: RemoveContactFromListEvent) {
        val userId = evt.userId
        val periodId = evt.periodId
        if (userId == null || periodId == null) return
        job.removeFromList(userId, periodId)
    }
}
