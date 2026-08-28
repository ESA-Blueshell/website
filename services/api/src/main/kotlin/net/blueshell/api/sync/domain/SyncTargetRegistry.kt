package net.blueshell.api.sync.domain

import org.springframework.stereotype.Component

/** Lookup of all registered [SyncTarget] beans. The driver fans out per aggregate type. */
@Component
class SyncTargetRegistry(
    targets: List<SyncTarget<*>>,
) {
    private val contactTargets: List<ContactSyncTarget> = targets.filterIsInstance<ContactSyncTarget>()
    private val calendarTargets: List<CalendarSyncTarget> = targets.filterIsInstance<CalendarSyncTarget>()

    fun forContact(): List<ContactSyncTarget> = contactTargets
    fun forCalendar(): List<CalendarSyncTarget> = calendarTargets
}
