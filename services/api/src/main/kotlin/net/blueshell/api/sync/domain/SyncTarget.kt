package net.blueshell.api.sync.domain

import net.blueshell.api.event.domain.CalendarEventData
import net.blueshell.api.contact.api.ContactData
import net.blueshell.api.shared.enums.TargetSystem
import net.blueshell.api.event.api.CalendarEventData

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
