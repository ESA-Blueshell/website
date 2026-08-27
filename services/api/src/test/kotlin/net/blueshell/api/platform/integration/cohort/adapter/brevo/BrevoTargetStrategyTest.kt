package net.blueshell.api.platform.integration.cohort.adapter.brevo

import net.blueshell.api.platform.integration.contact.adapter.ContactListAdapter
import net.blueshell.api.platform.integration.contact.adapter.ContactServiceException
import net.blueshell.api.shared.enums.ContactSystem
import net.blueshell.clients.brevo.api.ContactsApi
import net.blueshell.clients.brevo.model.GetFolder
import net.blueshell.clients.brevo.model.GetFolders200Response
import net.blueshell.clients.brevo.model.GetLists200Response
import net.blueshell.clients.brevo.model.GetLists200ResponseListsInner
import net.blueshell.clients.brevo.model.GetProcessesSortParameter
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.web.client.RestClientResponseException

class BrevoTargetStrategyTest {
    private val contactsApi: ContactsApi = mock()
    private val lists: ContactListAdapter = mock {
        whenever(it.system).thenReturn(ContactSystem.BREVO)
    }
    private val strategy = BrevoTargetStrategy(listOf(lists), contactsApi)

    @Test
    fun `pages folders and lists and maps folder names and counts`() {
        whenever(contactsApi.getFolders(eq(50L), eq(0L), eq(GetProcessesSortParameter.ASC)))
            .thenReturn(GetFolders200Response().count(51).folders((1L..50L).map { folder(it, "Folder $it") }))
        whenever(contactsApi.getFolders(eq(50L), eq(50L), eq(GetProcessesSortParameter.ASC)))
            .thenReturn(GetFolders200Response().count(51).folders(listOf(folder(51L, "Contribution periods"))))
        whenever(contactsApi.getLists(eq(50L), eq(0L), eq(GetProcessesSortParameter.ASC)))
            .thenReturn(GetLists200Response().count(51).lists((1L..50L).map { list(it, "List $it", 1L) }))
        whenever(contactsApi.getLists(eq(50L), eq(50L), eq(GetProcessesSortParameter.ASC)))
            .thenReturn(GetLists200Response().count(51).lists(listOf(list(99L, "Paid 2026", 51L, 728L))))

        val targets = strategy.catalog(null)
        val paid = targets.single { it.externalId == "99" }

        assertThat(targets).hasSize(51)
        assertThat(paid.folderLabel).isEqualTo("Contribution periods")
        assertThat(paid.memberCount).isEqualTo(728L)
    }

    @Test
    fun `says where each list sits, outside in`() {
        whenever(contactsApi.getFolders(eq(50L), eq(0L), eq(GetProcessesSortParameter.ASC)))
            .thenReturn(GetFolders200Response().count(1).folders(listOf(folder(1L, "Committees"))))
        whenever(contactsApi.getLists(eq(50L), eq(0L), eq(GetProcessesSortParameter.ASC)))
            .thenReturn(
                GetLists200Response().count(2)
                    .lists(listOf(list(10L, "Web Cmte", 1L), list(11L, "Loose ends", 404L))),
            )

        val targets = strategy.catalog(null).associateBy { it.externalId }

        // The system first, then the folder holding the list: enough to tell two lists that
        // share a name apart.
        assertThat(targets.getValue("10").path).containsExactly("Brevo", "Committees")
        // A list in a folder Brevo did not name is not in an anonymous folder — it is loose.
        assertThat(targets.getValue("11").path).containsExactly("Brevo")
    }

    @Test
    fun `filters by query after fetching the bounded catalog`() {
        whenever(contactsApi.getFolders(eq(50L), eq(0L), eq(GetProcessesSortParameter.ASC)))
            .thenReturn(GetFolders200Response().count(1).folders(listOf(folder(1L, "Members"))))
        whenever(contactsApi.getLists(eq(50L), eq(0L), eq(GetProcessesSortParameter.ASC)))
            .thenReturn(GetLists200Response().count(2).lists(listOf(list(10L, "Guests", 1L), list(11L, "Paid", 1L))))

        val targets = strategy.catalog("paid")

        assertThat(targets).extracting<String> { it.externalId }.containsExactly("11")
    }

    @Test
    fun `treats Brevo rate limiting as retryable`() {
        doThrow(error(429)).whenever(contactsApi)
            .getFolders(eq(50L), eq(0L), eq(GetProcessesSortParameter.ASC))

        assertThatThrownBy { strategy.catalog(null) }
            .isInstanceOf(ContactServiceException::class.java)
            .hasCauseInstanceOf(RestClientResponseException::class.java)
    }

    private fun folder(id: Long, name: String): GetFolder =
        GetFolder().id(id).name(name)

    private fun list(
        id: Long,
        name: String,
        folderId: Long,
        unique: Long = 10L + id,
    ): GetLists200ResponseListsInner =
        GetLists200ResponseListsInner()
            .id(id)
            .name(name)
            .folderId(folderId)
            .uniqueSubscribers(unique)

    private fun error(status: Int): RestClientResponseException =
        RestClientResponseException("$status error", status, "error", null, ByteArray(0), null)
}
