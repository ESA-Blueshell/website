package net.blueshell.api.sync.domain

import net.blueshell.api.event.api.CalendarAdapter
import net.blueshell.api.event.api.CalendarEventData
import net.blueshell.api.shared.enums.TargetSystem
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component

@Component
@Profile("!test & !dev")
class GoogleCalendarEventSyncTarget(
    private val adapter: CalendarAdapter,
) : CalendarSyncTarget {
    override val system = TargetSystem.GOOGLE_CALENDAR

    override fun push(aggregateId: Long, data: CalendarEventData?, currentExternalId: String?): String? = when {
        data == null && currentExternalId == null -> null
        data == null -> { adapter.removeEvent(aggregateId, currentExternalId!!); null }
        else -> adapter.syncEvent(aggregateId, data, currentExternalId)?.externalId
    }
}
