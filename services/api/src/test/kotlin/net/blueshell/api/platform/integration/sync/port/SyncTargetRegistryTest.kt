package net.blueshell.api.platform.integration.sync.port

import net.blueshell.api.domain.event.application.calendar.CalendarEventData
import net.blueshell.api.contact.api.ContactData
import net.blueshell.api.shared.enums.TargetSystem
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SyncTargetRegistryTest {

    private val contact = object : ContactSyncTarget {
        override val system = TargetSystem.BREVO
        override fun push(aggregateId: Long, data: ContactData?, currentExternalId: String?) = null
    }
    private val calendar = object : CalendarSyncTarget {
        override val system = TargetSystem.GOOGLE_CALENDAR
        override fun push(aggregateId: Long, data: CalendarEventData?, currentExternalId: String?) = null
    }

    @Test
    fun `partitions targets by aggregate type`() {
        val registry = SyncTargetRegistry(listOf(contact, calendar))
        assertEquals(listOf(contact), registry.forContact())
        assertEquals(listOf(calendar), registry.forCalendar())
    }

    @Test
    fun `empty list yields empty buckets`() {
        val registry = SyncTargetRegistry(emptyList())
        assertEquals(emptyList<ContactSyncTarget>(), registry.forContact())
        assertEquals(emptyList<CalendarSyncTarget>(), registry.forCalendar())
    }
}
