package net.blueshell.api.platform.integration.sync.port

import net.blueshell.api.domain.event.application.calendar.CalendarEventData
import net.blueshell.api.platform.integration.contact.adapter.ContactData

/**
 * External system this app pushes aggregate state to. Persisted as a string in
 * `external_id_mapping.system`.
 *
 * Adding a new target (e.g. `GOOGLE_WORKSPACE`, `DISCORD`) is one new enum
 * value here plus one new `SyncTarget` implementation; the fan-out driver and
 * the mapping table need no further change.
 */
enum class TargetSystem { BREVO, GOOGLE_CALENDAR }

/** Kind of aggregate a target syncs. Persisted as a string in `external_id_mapping.aggregate_type`. */
enum class AggregateType { USER, EVENT, CONTACT_LIST }

/**
 * Pushes one aggregate's current state to one external system.
 *
 * Contract: `data == null` means "this aggregate should not exist in the
 * external system". Return value is the external id after the push, or `null`
 * if the aggregate was removed.
 */
interface SyncTarget<A : Any> {
    val system: TargetSystem
    val aggregateType: AggregateType
    fun push(aggregateId: Long, data: A?, currentExternalId: String?): String?
}

interface ContactSyncTarget : SyncTarget<ContactData> {
    override val aggregateType: AggregateType get() = AggregateType.USER
}

interface CalendarSyncTarget : SyncTarget<CalendarEventData> {
    override val aggregateType: AggregateType get() = AggregateType.EVENT
}
