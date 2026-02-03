package net.blueshell.api.listener.contact

import net.blueshell.api.common.event.job.SyncContactEvent
import net.blueshell.api.job.contact.SyncContactJob
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

@Component
class SyncContactEventListener(
    private val job: SyncContactJob
) {
    @EventListener
    fun onSync(evt: SyncContactEvent) {
        val userId = evt.userId
        if (userId == null) return
        job.sync(userId)
    }
}
