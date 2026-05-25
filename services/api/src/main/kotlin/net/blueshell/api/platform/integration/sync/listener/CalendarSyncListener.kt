package net.blueshell.api.platform.integration.sync.listener

import net.blueshell.api.domain.event.application.event.EventChanged
import net.blueshell.api.platform.integration.sync.application.CalendarSyncService
import org.springframework.modulith.events.ApplicationModuleListener
import org.springframework.stereotype.Component

/** Modulith listener that fans event-changed out to every calendar target. */
@Component
class CalendarSyncListener(
    private val service: CalendarSyncService,
) {
    @ApplicationModuleListener
    fun on(event: EventChanged) = service.sync(event.eventId)
}
