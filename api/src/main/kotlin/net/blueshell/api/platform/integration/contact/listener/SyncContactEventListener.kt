package net.blueshell.api.platform.integration.contact.listener

import net.blueshell.api.platform.integration.event.job.SyncContactEvent
import net.blueshell.api.platform.integration.contact.job.SyncContactJob
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

@Component
class SyncContactEventListener(
    private val job: SyncContactJob
) {
    @EventListener
    fun onSync(evt: SyncContactEvent) {
        val userId = evt.userId ?: return
        job.sync(userId)
    }
}
