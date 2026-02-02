package net.blueshell.api.listener.contact

import lombok.RequiredArgsConstructor
import lombok.extern.slf4j.Slf4j
import net.blueshell.api.common.event.job.RemoveContactFromListEvent
import net.blueshell.api.job.contact.RemoveContactFromListJob
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

@Slf4j
@Component
@RequiredArgsConstructor
class RemoveContactFromListEventListener {
    private val job: RemoveContactFromListJob? = null

    @EventListener
    fun onRemove(evt: RemoveContactFromListEvent) {
        val userId = evt.userId
        val periodId = evt.periodId
        if (userId == null || periodId == null) return
        job!!.removeFromList(userId, periodId)
    }
}