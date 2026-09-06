package net.blueshell.api.cohort.domain

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.blueshell.api.contact.api.ContactListAdapter
import net.blueshell.api.shared.enums.ContactSystem
import net.blueshell.api.cohort.persistence.CohortKind
import net.blueshell.api.shared.enums.TargetSystem
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

/**
 * Moving a list is the one write where the folder is named rather than chosen by id, so the
 * name has to resolve to a folder that exists before anything is sent.
 */
class TargetFolderMoveTest {

    // The strategy picks its adapter out of the list by system, so the mock has to claim one.
    private val lists = mockk<ContactListAdapter>(relaxed = true) {
        every { system } returns ContactSystem.BREVO
    }
    private val strategy = BrevoTargetStrategy(listOf(lists))

    private val target = ExternalTarget(
        system = TargetSystem.BREVO,
        externalId = "42",
        kind = CohortKind.LIST,
        label = "Members 2025",
        folderLabel = "Old folder",
    )

    @Test
    fun `the strategy declares that it can move`() {
        assertThat(strategy.descriptor.supports(TargetCapability.MOVE)).isTrue()
    }

    @Test
    fun `a named folder is resolved to the id the system files by`() {
        every { lists.listFolders() } returns mapOf(7L to "Contribution periods", 9L to "Newsletter")

        val moved = strategy.move(target, "Newsletter")

        verify(exactly = 1) { lists.moveList(42L, 9L) }
        assertThat(moved.folderLabel).isEqualTo("Newsletter")
        assertThat(moved.externalId).isEqualTo("42")
    }

    @Test
    fun `the name is matched however it is capitalised`() {
        every { lists.listFolders() } returns mapOf(9L to "Newsletter")

        strategy.move(target, "newsletter")

        verify { lists.moveList(42L, 9L) }
    }

    @Test
    fun `an unknown folder is refused rather than created`() {
        every { lists.listFolders() } returns mapOf(9L to "Newsletter")

        assertThatThrownBy { strategy.move(target, "Somewhere else") }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("No folder named")

        // Nothing is sent, so a typo cannot leave a near-duplicate folder behind.
        verify(exactly = 0) { lists.moveList(any(), any()) }
    }

    @Test
    fun `folders are read from the system, including the empty ones`() {
        every { lists.listFolders() } returns mapOf(2L to "Zeta", 1L to "Alpha")

        // Inferring them from the lists that sit in them would hide a folder holding none,
        // which is exactly where a target is most likely headed.
        assertThat(strategy.folders()).containsExactly("Alpha", "Zeta")
    }
}
