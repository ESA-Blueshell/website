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
        val listmonk = target(TargetSystem.LISTMONK, "lm-1")
        val brevo = target(TargetSystem.BREVO, "br-2")

        fanOut.push("USER", 42L, data, listOf(listmonk, brevo))

        verify(listmonk).push(eq(42L), eq(data), eq(null))
        verify(brevo).push(eq(42L), eq(data), eq(null))
    }

    @Test
    fun `persists each target's external id under the right system tag`() {
        val listmonk = target(TargetSystem.LISTMONK, "lm-1")
        val brevo = target(TargetSystem.BREVO, "br-2")

        fanOut.push("USER", 42L, data, listOf(listmonk, brevo))

        verify(mappings).upsert("USER", 42L, "LISTMONK", "lm-1")
        verify(mappings).upsert("USER", 42L, "BREVO", "br-2")
    }

    @Test
    fun `passes the stored external id to each target's push for updates`() {
        val listmonk = target(TargetSystem.LISTMONK, "lm-1")
        val brevo = target(TargetSystem.BREVO, "br-2")
        whenever(mappings.find("USER", 42L, "LISTMONK"))
            .thenReturn(ExternalIdMapping("USER", 42L, "LISTMONK", "lm-1"))
        whenever(mappings.find("USER", 42L, "BREVO"))
            .thenReturn(ExternalIdMapping("USER", 42L, "BREVO", "br-2"))

        fanOut.push("USER", 42L, data, listOf(listmonk, brevo))

        verify(listmonk).push(eq(42L), eq(data), eq("lm-1"))
        verify(brevo).push(eq(42L), eq(data), eq("br-2"))
    }

    @Test
    fun `invokes the postPush callback once per target with the new external id`() {
        val listmonk = target(TargetSystem.LISTMONK, "lm-1")
        val brevo = target(TargetSystem.BREVO, "br-2")
        val seen = mutableListOf<Pair<TargetSystem, String?>>()

        fanOut.push("USER", 42L, data, listOf(listmonk, brevo)) { system, id ->
            seen += system to id
        }

        assert(seen == listOf(TargetSystem.LISTMONK to "lm-1", TargetSystem.BREVO to "br-2"))
    }

    @Test
    fun `null data signals removal and pushes through to every target`() {
        val listmonk = target(TargetSystem.LISTMONK, null)
        val brevo = target(TargetSystem.BREVO, null)
        whenever(mappings.find("USER", 42L, "LISTMONK"))
            .thenReturn(ExternalIdMapping("USER", 42L, "LISTMONK", "lm-1"))
        whenever(mappings.find("USER", 42L, "BREVO"))
            .thenReturn(ExternalIdMapping("USER", 42L, "BREVO", "br-2"))

        fanOut.push("USER", 42L, null as ContactData?, listOf(listmonk, brevo))

        verify(listmonk).push(eq(42L), eq(null), eq("lm-1"))
        verify(brevo).push(eq(42L), eq(null), eq("br-2"))
        verify(mappings).upsert("USER", 42L, "LISTMONK", null)
        verify(mappings).upsert("USER", 42L, "BREVO", null)
    }

    @Test
    fun `empty target list does nothing`() {
        fanOut.push("USER", 42L, data, emptyList<ContactSyncTarget>())

        verify(mappings, never()).find(any(), any(), any())
        verify(mappings, never()).upsert(any(), any(), any(), any())
    }
}
