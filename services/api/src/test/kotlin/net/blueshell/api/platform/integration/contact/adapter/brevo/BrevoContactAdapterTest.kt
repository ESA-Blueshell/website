package net.blueshell.api.platform.integration.contact.adapter.brevo

import net.blueshell.api.platform.integration.contact.adapter.ContactData
import net.blueshell.clients.brevo.api.ContactsApi
import net.blueshell.clients.brevo.model.CreateContact201Response
import net.blueshell.clients.brevo.model.CreateContactRequest
import net.blueshell.clients.brevo.model.GetContactInfo200Response
import net.blueshell.clients.brevo.model.UpdateContactRequest
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.web.client.RestClientResponseException
import tools.jackson.databind.json.JsonMapper

class BrevoContactAdapterTest {

    private val contactsApi: ContactsApi = mock()
    private val adapter = BrevoContactAdapter(contactsApi, JsonMapper.builder().build())

    private val data = ContactData(
        email = "alice@example.com",
        firstName = "Alice",
        lastName = "Smith",
        phoneNumber = "+31612345678",
        newsletter = true,
        isMember = true,
    )

    @Test
    fun `createContact returns the new id on success`() {
        whenever(contactsApi.createContact(any())).thenReturn(CreateContact201Response().id(42L))

        assertThat(adapter.createContact(data)).isEqualTo(42L)
    }

    @Test
    fun `duplicate email adopts the existing contact and pushes attributes`() {
        whenever(contactsApi.createContact(any())).thenThrow(duplicateError("email"))
        whenever(contactsApi.getContactInfo(eq(data.email), eq("email_id"), anyOrNull(), anyOrNull()))
            .thenReturn(GetContactInfo200Response().id(99L))

        val id = adapter.createContact(data)

        assertThat(id).isEqualTo(99L)
        verify(contactsApi).updateContact(eq(data.email), any<UpdateContactRequest>(), eq("email_id"))
    }

    @Test
    fun `duplicate SMS adopts the existing contact looked up by phone`() {
        whenever(contactsApi.createContact(any())).thenThrow(duplicateError("SMS"))
        whenever(contactsApi.getContactInfo(eq(data.phoneNumber!!), eq("phone_id"), anyOrNull(), anyOrNull()))
            .thenReturn(GetContactInfo200Response().id(77L))

        assertThat(adapter.createContact(data)).isEqualTo(77L)
    }

    @Test
    fun `duplicate that cannot be resolved throws BrevoDuplicateContactException`() {
        whenever(contactsApi.createContact(any())).thenThrow(duplicateError("email"))
        whenever(contactsApi.getContactInfo(any(), any(), anyOrNull(), anyOrNull()))
            .thenThrow(notFound())

        assertThatThrownBy { adapter.createContact(data) }
            .isInstanceOf(BrevoDuplicateContactException::class.java)
        verify(contactsApi, never()).updateContact(any(), any<UpdateContactRequest>(), any())
    }

    @Test
    fun `non-duplicate error surfaces as BrevoApiException with parsed code`() {
        whenever(contactsApi.createContact(any())).thenThrow(
            error(400, """{"code":"invalid_parameter","message":"Invalid email"}"""),
        )

        assertThatThrownBy { adapter.createContact(data) }
            .isInstanceOf(BrevoApiException::class.java)
            .hasMessageContaining("invalid_parameter")
            .hasMessageContaining("Invalid email")
    }

    private fun duplicateError(identifier: String): RestClientResponseException =
        error(
            400,
            """{"code":"duplicate_parameter","message":"$identifier is already associated with another Contact",""" +
                """"metadata":{"duplicate_identifiers":["$identifier"]}}""",
        )

    private fun notFound(): RestClientResponseException = error(404, """{"code":"document_not_found"}""")

    private fun error(status: Int, body: String): RestClientResponseException =
        RestClientResponseException(
            "$status error",
            status,
            "error",
            null,
            body.toByteArray(),
            null,
        )
}
