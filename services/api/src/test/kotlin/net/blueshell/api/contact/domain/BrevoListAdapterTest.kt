package net.blueshell.api.contact.domain

import net.blueshell.api.contact.api.ContactListRef
import net.blueshell.api.contact.api.ContactServiceException
import net.blueshell.clients.brevo.api.ContactsApi
import net.blueshell.clients.brevo.model.AddContactToListRequest
import net.blueshell.clients.brevo.model.GetContactInfo200Response
import net.blueshell.clients.brevo.model.GetContactInfo200ResponseAllOfStatistics
import net.blueshell.clients.brevo.model.GetContactsSortParameter
import net.blueshell.clients.brevo.model.GetFolder
import net.blueshell.clients.brevo.model.GetFolders200Response
import net.blueshell.clients.brevo.model.GetLists200Response
import net.blueshell.clients.brevo.model.GetLists200ResponseListsInner
import net.blueshell.clients.brevo.model.RemoveContactFromListRequest
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.web.client.RestClientResponseException
import tools.jackson.databind.json.JsonMapper

class BrevoListAdapterTest {

    private val contactsApi: ContactsApi = mock()
    private val adapter = BrevoListAdapter(
        contactsApi = contactsApi,
        jsonMapper = JsonMapper.builder().build(),
        contributionPeriodsFolder = 7L,
    )

    @Test
    fun `addToList treats already-in-list as idempotent success when the contact exists`() {
        doThrow(alreadyInListOrMissing()).whenever(contactsApi)
            .addContactToList(eq(200L), any<AddContactToListRequest>())
        whenever(contactsApi.getContactInfo(eq("100"), eq("contact_id"), anyOrNull(), anyOrNull()))
            .thenReturn(contactInfo(100L))

        // No throw expected: Brevo's ambiguous message is resolved to "already there".
        adapter.addToList(externalUserId = 100L, externalListId = 200L)
    }

    @Test
    fun `addToList throws ExternalContactGoneException when the contact does not exist`() {
        doThrow(alreadyInListOrMissing()).whenever(contactsApi)
            .addContactToList(eq(200L), any<AddContactToListRequest>())
        doThrow(error(404, """{"code":"document_not_found"}""")).whenever(contactsApi)
            .getContactInfo(eq("100"), eq("contact_id"), anyOrNull(), anyOrNull())

        assertThatThrownBy { adapter.addToList(100L, 200L) }
            .isInstanceOf(ExternalContactGoneException::class.java)
    }

    @Test
    fun `addToList treats inconclusive lookup as retryable, not as a missing contact`() {
        // Brevo returned the ambiguous "already in list and/or does not exist",
        // and the disambiguating GET hit a transient 503 instead of a clean
        // 404. We must not churn local pairing on a provider outage — surface
        // a plain ContactServiceException so the job retries.
        doThrow(alreadyInListOrMissing()).whenever(contactsApi)
            .addContactToList(eq(200L), any<AddContactToListRequest>())
        doThrow(error(503, """{"code":"server_error","message":"Upstream timeout"}"""))
            .whenever(contactsApi).getContactInfo(eq("100"), eq("contact_id"), anyOrNull(), anyOrNull())

        assertThatThrownBy { adapter.addToList(100L, 200L) }
            .isInstanceOf(ContactServiceException::class.java)
            .isNotInstanceOf(ExternalContactGoneException::class.java)
    }

    @Test
    fun `addToList surfaces other errors as ContactServiceException`() {
        doThrow(error(500, """{"code":"internal","message":"boom"}"""))
            .whenever(contactsApi).addContactToList(any(), any<AddContactToListRequest>())

        assertThatThrownBy { adapter.addToList(100L, 200L) }
            .isInstanceOf(ContactServiceException::class.java)
    }

    @Test
    fun `removeFromList treats already-removed as idempotent success`() {
        doThrow(alreadyInListOrMissing()).whenever(contactsApi)
            .removeContactFromList(eq(200L), any<RemoveContactFromListRequest>())

        adapter.removeFromList(externalUserId = 100L, externalListId = 200L)
    }

    @Test
    fun `pages folders and lists past the first page`() {
        whenever(contactsApi.getFolders(eq(50L), eq(0L), eq(GetContactsSortParameter.ASC)))
            .thenReturn(GetFolders200Response(count = 51L, folders = (1L..50L).map { folder(it, "Folder $it") }))
        whenever(contactsApi.getFolders(eq(50L), eq(50L), eq(GetContactsSortParameter.ASC)))
            .thenReturn(GetFolders200Response(count = 51L, folders = listOf(folder(51L, "Contribution periods"))))
        whenever(contactsApi.getLists(eq(50L), eq(0L), eq(GetContactsSortParameter.ASC)))
            .thenReturn(GetLists200Response(count = 51L, lists = (1L..50L).map { list(it, "List $it", 1L) }))
        whenever(contactsApi.getLists(eq(50L), eq(50L), eq(GetContactsSortParameter.ASC)))
            .thenReturn(GetLists200Response(count = 51L, lists = listOf(list(99L, "Paid 2026", 51L, 728L))))

        assertThat(adapter.listFolders()).hasSize(51).containsEntry(51L, "Contribution periods")
        assertThat(adapter.listAll()).hasSize(51)
            .contains(ContactListRef(externalListId = 99L, name = "Paid 2026", folderId = 51L, memberCount = 728L))
    }

    @Test
    fun `surfaces a rate-limited catalog page as a retryable failure`() {
        doThrow(error(429, "")).whenever(contactsApi)
            .getFolders(eq(50L), eq(0L), eq(GetContactsSortParameter.ASC))

        assertThatThrownBy { adapter.listFolders() }
            .isInstanceOf(ContactServiceException::class.java)
            .hasCauseInstanceOf(RestClientResponseException::class.java)
    }

    // The generated models are immutable data classes, so the counts Brevo always returns have
    // to be supplied even where the assertions ignore them.
    private fun folder(id: Long, name: String): GetFolder =
        GetFolder(id = id, name = name, totalBlacklisted = 0L, totalSubscribers = 0L, uniqueSubscribers = 0L)

    private fun list(id: Long, name: String, folderId: Long, unique: Long = 10L + id): GetLists200ResponseListsInner =
        GetLists200ResponseListsInner(
            id = id,
            name = name,
            folderId = folderId,
            uniqueSubscribers = unique,
            totalBlacklisted = 0L,
            totalSubscribers = unique,
        )

    private fun alreadyInListOrMissing(): RestClientResponseException = error(
        400,
        """{"code":"invalid_parameter","message":"Contact already in list and/or does not exist"}""",
    )

    private fun error(status: Int, body: String): RestClientResponseException =
        RestClientResponseException("$status error", status, "error", null, body.toByteArray(), null)

    /**
     * A minimal [GetContactInfo200Response].
     *
     * The generated model is an immutable data class and Brevo always returns
     * these fields, so they have to be supplied even though only the id is
     * asserted on. Kept in one helper rather than repeated per stub.
     */
    private fun contactInfo(id: Long): GetContactInfo200Response =
        GetContactInfo200Response(
            id = id,
            email = "member@example.com",
            attributes = emptyMap<String, Any>(),
            createdAt = "2026-01-01T00:00:00.000Z",
            modifiedAt = "2026-01-01T00:00:00.000Z",
            listIds = emptyList(),
            emailBlacklisted = false,
            smsBlacklisted = false,
            whatsappBlacklisted = false,
            statistics = GetContactInfo200ResponseAllOfStatistics(),
        )

}
