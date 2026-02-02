package net.blueshell.api.listener.contact

import lombok.RequiredArgsConstructor
import lombok.extern.slf4j.Slf4j
import net.blueshell.api.common.event.job.SyncContactEvent
import net.blueshell.api.job.contact.SyncContactJob
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

@Slf4j
@Component
@RequiredArgsConstructor
class SyncContactEventListener {
    private val job: SyncContactJob? = null

    @EventListener
    fun onSync(evt: SyncContactEvent) {
        val userId = evt.userId
        if (userId == null) return
        job!!.sync(userId)
    }
}