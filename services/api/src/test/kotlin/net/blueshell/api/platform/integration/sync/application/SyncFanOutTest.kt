package net.blueshell.api.platform.integration.sync.application

import net.blueshell.api.platform.integration.contact.adapter.ContactData
import net.blueshell.api.platform.integration.sync.persistence.ExternalIdMapping
import net.blueshell.api.platform.integration.sync.port.ContactSyncTarget
import net.blueshell.api.platform.integration.sync.port.TargetSystem
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * The test exercises fan-out mechanics, not aggregate-type matching, so it
 * pairs `TargetSystem.BREVO` with a second placeholder system to demonstrate
 * iteration across multiple targets. The second is `GOOGLE_CALENDAR` purely
 * because no second contact target currently exists; when a real second
 * contact integration lands (Google Workspace, Discord) the test can be
 * retargeted with no shape change.
 */
class SyncFanOutTest {

    private val mappings: ExternalIdMappingService = mock()
    private val fanOut = SyncFanOut(mappings)

    private val data = ContactData(
        email = "a@b.c",
        firstName = "A",
        lastName = "B",
        phoneNumber = null,
        newsletter = false,
        isMember = false,
    )

    private fun target(system: TargetSystem, pushReturn: String? = "id-${system.name}"): ContactSyncTarget {
        val t: ContactSyncTarget = mock()
        whenever(t.system).thenReturn(system)
        whenever(t.push(any<Long>(), anyOrNull<ContactData>(), anyOrNull<String>())).thenReturn(pushReturn)
        return t
    }

    @Test
    fun `pushes the same data to every registered target`() {
        val first = target(TargetSystem.BREVO, "br-1")
        val second = target(TargetSystem.GOOGLE_CALENDAR, "gc-2")

        fanOut.push("USER", 42L, data, listOf(first, second))

        verify(first).push(eq(42L), eq(data), eq(null))
        verify(second).push(eq(42L), eq(data), eq(null))
    }

    @Test
    fun `persists each target's external id under the right system tag`() {
        val first = target(TargetSystem.BREVO, "br-1")
        val second = target(TargetSystem.GOOGLE_CALENDAR, "gc-2")

        fanOut.push("USER", 42L, data, listOf(first, second))

        verify(mappings).upsert("USER", 42L, "BREVO", "br-1")
        verify(mappings).upsert("USER", 42L, "GOOGLE_CALENDAR", "gc-2")
    }

    @Test
    fun `passes the stored external id to each target's push for updates`() {
        val first = target(TargetSystem.BREVO, "br-1")
        val second = target(TargetSystem.GOOGLE_CALENDAR, "gc-2")
        whenever(mappings.find("USER", 42L, "BREVO"))
            .thenReturn(ExternalIdMapping("USER", 42L, "BREVO", "br-1"))
        whenever(mappings.find("USER", 42L, "GOOGLE_CALENDAR"))
            .thenReturn(ExternalIdMapping("USER", 42L, "GOOGLE_CALENDAR", "gc-2"))

        fanOut.push("USER", 42L, data, listOf(first, second))

        verify(first).push(eq(42L), eq(data), eq("br-1"))
        verify(second).push(eq(42L), eq(data), eq("gc-2"))
    }

    @Test
    fun `invokes the postPush callback once per target with the new external id`() {
        val first = target(TargetSystem.BREVO, "br-1")
        val second = target(TargetSystem.GOOGLE_CALENDAR, "gc-2")
        val seen = mutableListOf<Pair<TargetSystem, String?>>()

        fanOut.push("USER", 42L, data, listOf(first, second)) { system, id ->
            seen += system to id
        }

        assert(seen == listOf(TargetSystem.BREVO to "br-1", TargetSystem.GOOGLE_CALENDAR to "gc-2"))
    }

    @Test
    fun `null data signals removal and pushes through to every target`() {
        val first = target(TargetSystem.BREVO, null)
        val second = target(TargetSystem.GOOGLE_CALENDAR, null)
        whenever(mappings.find("USER", 42L, "BREVO"))
            .thenReturn(ExternalIdMapping("USER", 42L, "BREVO", "br-1"))
        whenever(mappings.find("USER", 42L, "GOOGLE_CALENDAR"))
            .thenReturn(ExternalIdMapping("USER", 42L, "GOOGLE_CALENDAR", "gc-2"))

        fanOut.push("USER", 42L, null as ContactData?, listOf(first, second))

        verify(first).push(eq(42L), eq(null), eq("br-1"))
        verify(second).push(eq(42L), eq(null), eq("gc-2"))
        verify(mappings).upsert("USER", 42L, "BREVO", null)
        verify(mappings).upsert("USER", 42L, "GOOGLE_CALENDAR", null)
    }

    @Test
    fun `empty target list does nothing`() {
        fanOut.push("USER", 42L, data, emptyList<ContactSyncTarget>())

        verify(mappings, never()).find(any(), any(), any())
        verify(mappings, never()).upsert(any(), any(), any(), any())
    }
}
