package net.blueshell.api.cohort.domain

import net.blueshell.api.contact.api.ContactListAdapter
import net.blueshell.api.contact.api.ContactListRef
import net.blueshell.api.shared.enums.TargetSystem
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class BrevoTargetStrategyTest {
    private val lists: ContactListAdapter = mock {
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

    private fun list(id: Long, name: String, folderId: Long, unique: Long = 10L + id): ContactListRef =
        ContactListRef(externalListId = id, name = name, folderId = folderId, memberCount = unique)
}
