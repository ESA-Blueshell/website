package net.blueshell.api.cohort.domain

import net.blueshell.api.contact.api.ContactListAdapter
import net.blueshell.api.contact.api.ContactListRef
import net.blueshell.api.contact.api.ContactServiceException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class BrevoFoldersTest {
    private val lists: ContactListAdapter = mock()
    private val folders = BrevoFolders(lists)

    private fun list(
        id: Long,
        name: String,
        folderId: Long,
    ) = ContactListRef(externalListId = id, name = name, folderId = folderId, memberCount = 0)

    @Test
    fun `says each folder with what it holds, two of one name apart`() {
        whenever(lists.listFolders()).thenReturn(mapOf(9L to "Committees", 3L to "committees", 5L to "Boards"))
        whenever(lists.listAll()).thenReturn(listOf(list(10L, "Sitecie", 3L), list(11L, "Lancie", 3L), list(12L, "Board 60", 9L)))

        assertThat(folders.states()).containsExactly(
            FolderState("5", "Boards", 0),
            FolderState("3", "committees", 2),
            FolderState("9", "Committees", 1),
        )
    }

    @Test
    fun `merging files the lists of a later folder under the oldest of its name, and removes it once empty`() {
        whenever(lists.listFolders()).thenReturn(mapOf(9L to "Committees", 3L to "committees", 5L to "Boards"))
        whenever(lists.listAll())
            .thenReturn(listOf(list(10L, "Sitecie", 3L), list(12L, "Lancie", 9L)))
            .thenReturn(listOf(list(10L, "Sitecie", 3L), list(12L, "Lancie", 3L)))

        assertThat(folders.merge()).isEqualTo(FolderMerge(removed = 1, moved = 1))

        verify(lists).moveList(12L, 3L)
        verify(lists).deleteFolder(9L)
        verify(lists, never()).deleteFolder(3L)
    }

    @Test
    fun `merging keeps a folder that still holds a list, since Brevo would remove the list with it`() {
        whenever(lists.listFolders()).thenReturn(mapOf(9L to "Committees", 3L to "Committees"))
        whenever(lists.listAll()).thenReturn(listOf(list(12L, "Lancie", 9L)))

        assertThat(folders.merge()).isEqualTo(FolderMerge(removed = 0, moved = 1))

        verify(lists, never()).deleteFolder(org.mockito.kotlin.any())
    }

    @Test
    fun `removes a folder that holds nothing, and refuses one that holds a list`() {
        whenever(lists.listAll()).thenReturn(listOf(list(12L, "Lancie", 9L)))

        folders.remove("5")
        verify(lists).deleteFolder(5L)

        assertThatThrownBy { folders.remove("9") }
            .isInstanceOf(ContactServiceException::class.java)
            .hasMessage("The folder still holds lists")
        verify(lists, never()).deleteFolder(9L)
    }

    @Test
    fun `refuses a folder id that is not Brevo's`() {
        assertThatThrownBy { folders.remove("committees") }.isInstanceOf(ContactServiceException::class.java)
    }
}
