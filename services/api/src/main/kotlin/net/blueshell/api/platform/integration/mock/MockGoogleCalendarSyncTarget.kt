package net.blueshell.api.platform.integration.mock

import net.blueshell.api.event.api.CalendarEventData
import net.blueshell.api.sync.domain.CalendarSyncTarget
import net.blueshell.api.shared.enums.TargetSystem
import org.springframework.context.annotation.Primary
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component

/** Test/dev calendar target backed by [MockCalendarAdapter]. */
@Component
@Primary
@Profile("test | dev")
class MockGoogleCalendarSyncTarget(
    private val adapter: MockCalendarAdapter,
) : CalendarSyncTarget {
    override val system = TargetSystem.GOOGLE_CALENDAR

    override fun push(aggregateId: Long, data: CalendarEventData?, currentExternalId: String?): String? = when {
        data == null && currentExternalId == null -> null
        data == null -> { adapter.removeEvent(aggregateId, currentExternalId!!); null }
        else -> adapter.syncEvent(aggregateId, data, currentExternalId)?.externalId
    }
}
