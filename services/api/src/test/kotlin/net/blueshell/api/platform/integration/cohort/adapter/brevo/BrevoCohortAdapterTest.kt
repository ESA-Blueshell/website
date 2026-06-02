package net.blueshell.api.platform.integration.cohort.adapter.brevo

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.blueshell.api.platform.integration.contact.adapter.ContactListAdapter
import net.blueshell.api.platform.integration.sync.port.TargetSystem
import net.blueshell.api.shared.enums.ContactSystem
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class BrevoCohortAdapterTest {

    private val brevoLegacy: ContactListAdapter = mockk {
        every { system } returns ContactSystem.BREVO
    }
    private val adapter = BrevoCohortAdapter(listOf(brevoLegacy))

    @Test
    fun `system is TargetSystem BREVO`() {
        assertThat(adapter.system).isEqualTo(TargetSystem.BREVO)
    }

    @Test
    fun `createCohort delegates to the legacy adapter and stringifies the returned id`() {
        every { brevoLegacy.createList("Members", "contributionPeriods") } returns 42L

        val externalId = adapter.createCohort("Members", "contributionPeriods")

        assertThat(externalId).isEqualTo("42")
    }

    @Test
    fun `createCohort passes a null hint through to the delegate`() {
        every { brevoLegacy.createList("Members", null) } returns 7L

        val externalId = adapter.createCohort("Members")

        assertThat(externalId).isEqualTo("7")
        verify { brevoLegacy.createList("Members", null) }
    }

    @Test
    fun `addMember parses string ids and delegates to addToList`() {
        every { brevoLegacy.addToList(123L, 456L) } returns Unit

        adapter.addMember("123", "456")

        verify { brevoLegacy.addToList(123L, 456L) }
    }

    @Test
    fun `removeMember parses string ids and delegates to removeFromList`() {
        every { brevoLegacy.removeFromList(123L, 456L) } returns Unit

        adapter.removeMember("123", "456")

        verify { brevoLegacy.removeFromList(123L, 456L) }
    }

    @Test
    fun `deleteCohort delegates to deleteList`() {
        every { brevoLegacy.deleteList(456L) } returns Unit

        adapter.deleteCohort("456")

        verify { brevoLegacy.deleteList(456L) }
    }
}
