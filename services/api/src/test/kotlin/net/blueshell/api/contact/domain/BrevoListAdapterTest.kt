package net.blueshell.api.contact.domain

import net.blueshell.api.contact.api.ContactServiceException
import net.blueshell.clients.brevo.api.ContactsApi
import net.blueshell.clients.brevo.model.AddContactToListRequest
import net.blueshell.clients.brevo.model.GetContactInfo200Response
import net.blueshell.clients.brevo.model.GetContactInfo200ResponseAllOfStatistics
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
