package net.blueshell.api.cohort.domain

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.blueshell.api.contact.api.ContactListAdapter
import net.blueshell.api.shared.enums.TargetSystem
import net.blueshell.api.shared.enums.ContactSystem
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
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

    @Test
    fun `addMember throws InvalidExternalIdException when externalUserId is malformed`() {
        assertThatThrownBy { adapter.addMember("not-a-number", "456") }
            .isInstanceOf(InvalidExternalIdException::class.java)
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("not-a-number")
            .hasMessageContaining("externalUserId")
            .hasMessageContaining("addMember")
    }

    @Test
    fun `addMember throws InvalidExternalIdException when externalCohortId is malformed`() {
        assertThatThrownBy { adapter.addMember("123", "bad-cohort") }
            .isInstanceOf(InvalidExternalIdException::class.java)
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("bad-cohort")
            .hasMessageContaining("externalCohortId")
            .hasMessageContaining("addMember")
    }

    @Test
    fun `removeMember throws InvalidExternalIdException when externalUserId is malformed`() {
        assertThatThrownBy { adapter.removeMember("oops", "456") }
            .isInstanceOf(InvalidExternalIdException::class.java)
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("oops")
            .hasMessageContaining("externalUserId")
            .hasMessageContaining("removeMember")
    }

    @Test
    fun `removeMember throws InvalidExternalIdException when externalCohortId is malformed`() {
        assertThatThrownBy { adapter.removeMember("123", "oops") }
            .isInstanceOf(InvalidExternalIdException::class.java)
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("oops")
            .hasMessageContaining("externalCohortId")
            .hasMessageContaining("removeMember")
    }

    @Test
    fun `deleteCohort throws InvalidExternalIdException when externalCohortId is malformed`() {
        assertThatThrownBy { adapter.deleteCohort("not-a-long") }
            .isInstanceOf(InvalidExternalIdException::class.java)
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("not-a-long")
            .hasMessageContaining("externalCohortId")
            .hasMessageContaining("deleteCohort")
    }

    @Test
    fun `listMembers throws InvalidExternalIdException when externalCohortId is malformed`() {
        assertThatThrownBy { adapter.listMembers("xyz") }
            .isInstanceOf(InvalidExternalIdException::class.java)
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("xyz")
            .hasMessageContaining("externalCohortId")
            .hasMessageContaining("listMembers")
    }
}
