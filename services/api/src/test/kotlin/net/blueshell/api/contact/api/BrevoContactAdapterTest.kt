package net.blueshell.api.contact.api

import net.blueshell.clients.brevo.api.ContactsApi
import net.blueshell.clients.brevo.model.CreateContact201Response
import net.blueshell.clients.brevo.model.CreateContactRequest
import net.blueshell.clients.brevo.model.GetContactInfo200Response
import net.blueshell.clients.brevo.model.GetContactInfo200ResponseAllOfStatistics
import net.blueshell.clients.brevo.model.UpdateContactRequest
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doNothing
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.web.client.RestClientResponseException
import tools.jackson.databind.json.JsonMapper
import net.blueshell.api.contact.domain.BrevoApiException
import net.blueshell.api.contact.domain.BrevoDuplicateContactException

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
        whenever(contactsApi.createContact(any())).thenReturn(CreateContact201Response(id = 42L))

        assertThat(adapter.createContact(data)).isEqualTo(42L)
    }

    @Test
    fun `duplicate email adopts the existing contact and updates by contact_id`() {
        whenever(contactsApi.createContact(any())).thenThrow(duplicateError("email"))
        whenever(contactsApi.getContactInfo(eq(data.email), eq("email_id"), anyOrNull(), anyOrNull()))
            .thenReturn(contactInfo(99L))

        val id = adapter.createContact(data)

        assertThat(id).isEqualTo(99L)
        // Critical: the adopt-then-update path must address the contact by its
        // resolved numeric id, not by the email it just collided on.
        verify(contactsApi).updateContact(eq("99"), any<UpdateContactRequest>(), eq("contact_id"))
    }

    @Test
    fun `phone-only duplicate on create drops SMS-WHATSAPP and retries instead of adopting by phone`() {
        // Adopting a stranger who happens to share a phone number would corrupt
        // the pairing. The right behaviour is to drop the conflicting phone
        // attributes and create a fresh contact without them.
        doThrow(duplicateError("SMS"))
            .doReturn(CreateContact201Response(id = 77L))
            .whenever(contactsApi).createContact(any())

        assertThat(adapter.createContact(data)).isEqualTo(77L)

        val captor = argumentCaptor<CreateContactRequest>()
        verify(contactsApi, org.mockito.kotlin.times(2)).createContact(captor.capture())
        val retry = captor.allValues.last()
        assertThat(retry.attributes!!.keys).doesNotContain("SMS", "WHATSAPP")
        // No phone-based lookup → no adoption.
        verify(contactsApi, never()).updateContact(any(), any<UpdateContactRequest>(), any())
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
    fun `non-duplicate create error surfaces as BrevoApiException with parsed code`() {
        whenever(contactsApi.createContact(any())).thenThrow(
            error(400, """{"code":"invalid_parameter","message":"Invalid email"}"""),
        )

        assertThatThrownBy { adapter.createContact(data) }
            .isInstanceOf(BrevoApiException::class.java)
            .hasMessageContaining("invalid_parameter")
            .hasMessageContaining("Invalid email")
    }

    @Test
    fun `invalid phone on create retries without SMS-WHATSAPP and succeeds`() {
        doThrow(error(400, """{"code":"invalid_parameter","message":"Invalid phone number"}"""))
            .doReturn(CreateContact201Response(id = 55L))
            .whenever(contactsApi).createContact(any())

        assertThat(adapter.createContact(data)).isEqualTo(55L)

        val captor = argumentCaptor<CreateContactRequest>()
        verify(contactsApi, org.mockito.kotlin.times(2)).createContact(captor.capture())
        val second = captor.allValues.last().attributes!!
        assertThat(second.keys).doesNotContain("SMS", "WHATSAPP")
    }

    @Test
    fun `updateContact uses contact_id with the supplied numeric id`() {
        doNothing().whenever(contactsApi).updateContact(any(), any<UpdateContactRequest>(), any())

        val returned = adapter.updateContact(123L, data)

        assertThat(returned).isEqualTo(123L)
        verify(contactsApi).updateContact(eq("123"), any<UpdateContactRequest>(), eq("contact_id"))
    }

    @Test
    fun `404 on updateContact triggers re-create and returns the new id`() {
        // The stored mapping points at a Brevo contact that no longer exists.
        // The adapter must self-heal by creating fresh (or adopting a different
        // existing one) and returning that new id so the orchestration layer
        // repairs the external_id_mapping.
        doThrow(error(404, """{"code":"document_not_found","message":"Contact does not exist"}"""))
            .whenever(contactsApi).updateContact(eq("888"), any<UpdateContactRequest>(), eq("contact_id"))
        whenever(contactsApi.createContact(any())).thenReturn(CreateContact201Response(id = 900L))

        val returned = adapter.updateContact(888L, data)

        assertThat(returned).isEqualTo(900L)
        verify(contactsApi).createContact(any())
    }

    @Test
    fun `duplicate_parameter on update retries with the conflicting attributes dropped`() {
        // First call: SMS / WHATSAPP / EXT_ID conflict on the resolved contact.
        // Second call (after dropping those attributes) succeeds — the rest of
        // the contact still gets synced.
        var callCount = 0
        doAnswer {
            callCount++
            if (callCount == 1) {
                throw error(
                    400,
                    """{"code":"duplicate_parameter","message":"Unable to update contact, SMS or WHATSAPP or EXT_ID are already associated","metadata":{"duplicate_identifiers":["SMS","WHATSAPP","EXT_ID"]}}""",
                )
            }
            null
        }.whenever(contactsApi).updateContact(any(), any<UpdateContactRequest>(), any())

        assertThat(adapter.updateContact(500L, data)).isEqualTo(500L)

        val captor = argumentCaptor<UpdateContactRequest>()
        verify(contactsApi, org.mockito.kotlin.times(2))
            .updateContact(eq("500"), captor.capture(), eq("contact_id"))
        val retry = captor.allValues.last()
        assertThat(retry.extId).isNull()
        assertThat(retry.attributes!!.keys).doesNotContain("SMS", "WHATSAPP")
    }

    @Test
    fun `invalid phone on update retries without SMS-WHATSAPP and succeeds`() {
        var callCount = 0
        doAnswer {
            callCount++
            if (callCount == 1) {
                throw error(400, """{"code":"invalid_parameter","message":"Invalid phone number"}""")
            }
            null
        }.whenever(contactsApi).updateContact(any(), any<UpdateContactRequest>(), any())

        assertThat(adapter.updateContact(601L, data)).isEqualTo(601L)

        val captor = argumentCaptor<UpdateContactRequest>()
        verify(contactsApi, org.mockito.kotlin.times(2))
            .updateContact(eq("601"), captor.capture(), eq("contact_id"))
        val retry = captor.allValues.last()
        assertThat(retry.attributes!!.keys).doesNotContain("SMS", "WHATSAPP")
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
