package net.blueshell.api.platform.integration.contact.adapter.brevo

import net.blueshell.api.platform.integration.contact.adapter.ContactServiceException
import net.blueshell.clients.brevo.api.ContactsApi
import net.blueshell.clients.brevo.model.AddContactToListRequest
import net.blueshell.clients.brevo.model.GetContactInfo200Response
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
            .thenReturn(GetContactInfo200Response().id(100L))

        // No throw expected: Brevo's ambiguous message is resolved to "already there".
        adapter.addToList(externalUserId = 100L, externalListId = 200L)
    }

    @Test
    fun `addToList throws BrevoContactGoneException when the contact does not exist`() {
        doThrow(alreadyInListOrMissing()).whenever(contactsApi)
            .addContactToList(eq(200L), any<AddContactToListRequest>())
        doThrow(error(404, """{"code":"document_not_found"}""")).whenever(contactsApi)
            .getContactInfo(eq("100"), eq("contact_id"), anyOrNull(), anyOrNull())

        assertThatThrownBy { adapter.addToList(100L, 200L) }
            .isInstanceOf(BrevoContactGoneException::class.java)
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
}
