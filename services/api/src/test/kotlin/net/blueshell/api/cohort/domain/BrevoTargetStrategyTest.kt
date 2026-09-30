package net.blueshell.api.cohort.domain

import net.blueshell.api.cohort.persistence.TargetKind
import net.blueshell.api.contact.api.ContactListAdapter
import net.blueshell.api.contact.api.ContactListMember
import net.blueshell.api.contact.api.ContactListRef
import net.blueshell.api.shared.enums.TargetSystem
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class BrevoTargetStrategyTest {
    private val lists: ContactListAdapter =
        mock {
            whenever(it.system).thenReturn(TargetSystem.BREVO)
        }
    private val strategy = BrevoTargetStrategy(listOf(lists))

    @Test
    fun `maps folder names and counts onto the catalog`() {
        whenever(lists.listFolders()).thenReturn(mapOf(51L to "Contribution periods"))
        whenever(lists.listAll()).thenReturn(listOf(list(99L, "Paid 2026", 51L, 728L)))

        val paid = strategy.catalog(null).single { it.externalId == "99" }

        assertThat(paid.folderLabel).isEqualTo("Contribution periods")
        assertThat(paid.memberCount).isEqualTo(728L)
    }

    @Test
    fun `renames a list and makes a folder by name`() {
        whenever(lists.listFolders()).thenReturn(mapOf(1L to "Archive"))
        val paid = ExternalTarget(TargetSystem.BREVO, "99", TargetKind.LIST, "Paid")

        assertThat(strategy.rename(paid, "Paid 2026").label).isEqualTo("Paid 2026")
        assertThat(strategy.createFolder("Archive")).containsExactly("Archive")
        verify(lists).renameList(99L, "Paid 2026")
        verify(lists).createFolder("Archive")
    }

    @Test
    fun `says where each list sits, outside in`() {
        whenever(lists.listFolders()).thenReturn(mapOf(1L to "Committees"))
        whenever(lists.listAll()).thenReturn(listOf(list(10L, "Web Cmte", 1L), list(11L, "Loose ends", 404L)))

        val targets = strategy.catalog(null).associateBy { it.externalId }

        // The system first, then the folder holding the list: enough to tell two lists that
        // share a name apart.
        assertThat(targets.getValue("10").path).containsExactly("Brevo", "Committees")
        // A list in a folder Brevo did not name is not in an anonymous folder — it is loose.
        assertThat(targets.getValue("11").path).containsExactly("Brevo")
    }

    @Test
    fun `filters by query after fetching the bounded catalog`() {
        whenever(lists.listFolders()).thenReturn(mapOf(1L to "Members"))
        whenever(lists.listAll()).thenReturn(listOf(list(10L, "Guests", 1L), list(11L, "Paid", 1L)))

        val targets = strategy.catalog("paid")

        assertThat(targets).extracting<String> { it.externalId }.containsExactly("11")
    }

    @Test
    fun `reads, adds to, removes from, renames and deletes a list by its Brevo id`() {
        val paid = ExternalTarget(TargetSystem.BREVO, "99", TargetKind.LIST, "Paid")
        whenever(lists.listMembers(99L)).thenReturn(listOf(ContactListMember(7L, "ada@example.com")))

        assertThat(strategy.members(paid)).containsExactly(ExternalMember("7", "ada@example.com"))
        strategy.add(paid, "7")
        strategy.remove(paid, "7")
        assertThat(strategy.rename(paid, "Paid 2026").label).isEqualTo("Paid 2026")
        strategy.delete(paid)

        verify(lists).addToList(7L, 99L)
        verify(lists).removeFromList(7L, 99L)
        verify(lists).renameList(99L, "Paid 2026")
        verify(lists).deleteList(99L)
    }

    private fun list(
        id: Long,
        name: String,
        folderId: Long,
        unique: Long = 10L + id,
    ): ContactListRef = ContactListRef(externalListId = id, name = name, folderId = folderId, memberCount = unique)
}
