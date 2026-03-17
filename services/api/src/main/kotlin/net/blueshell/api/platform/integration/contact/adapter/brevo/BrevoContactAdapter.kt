package net.blueshell.api.platform.integration.contact.adapter.brevo

import jakarta.validation.Valid
import net.blueshell.api.platform.integration.contact.adapter.ContactAdapter
import net.blueshell.api.platform.integration.contact.adapter.ContactData
import net.blueshell.api.platform.integration.contact.adapter.ContactServiceException
import net.blueshell.api.shared.enums.ContactSystem
import net.blueshell.clients.brevo.ApiClient
import net.blueshell.clients.brevo.api.ContactsApi
import net.blueshell.clients.brevo.model.CreateContactRequest
import net.blueshell.clients.brevo.model.CreateContactRequestAttributesValue
import net.blueshell.clients.brevo.model.UpdateContactRequest
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Profile
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientResponseException
import tools.jackson.databind.json.JsonMapper

/**
 * Brevo Anti-Corruption Layer (ADR-019)
 *
 * Implements [ContactSystemAdapter] against the Brevo Contacts API.
 * Active in production only (test/dev use MockContactAdapter).
 *
 * [contributionPeriodsFolder] is the Brevo folder ID under which all contribution-period
 * lists are created; the domain-level [folderName] hint is intentionally ignored because
 * Brevo organises lists by numeric folder ID, not by name.
 */
@Service
@Profile("!test & !dev")
class BrevoContactAdapter(
    restClientBuilder: RestClient.Builder,
    jsonMapper: JsonMapper,
) : ContactAdapter {

    @field:Value($$"${brevo.apiKey:}")
    lateinit var apiKey: String

    @field:Value($$"${brevo.baseUrl:https://api.brevo.com/v3}")
    lateinit var brevoBaseUrl: String

    override val system = ContactSystem.BREVO

    private val contactsApi: ContactsApi =
        ContactsApi(
            ApiClient(
                restClientBuilder.baseUrl(brevoBaseUrl).defaultHeader("api-key", apiKey)
                .configureMessageConverters {
                    it.addCustomConverter(JacksonJsonHttpMessageConverter(jsonMapper))
                }.build()))

    // ── Contact operations ─────────────────────────────────────────────────────

    override fun createContact(data: ContactData): Long {
        log.info("Creating Brevo contact: {}", data.email)
        return try {
            val req = CreateContactRequest()
            req.email = data.email
            req.extId = data.email   // Brevo extId used for dedup
            @Suppress("UNCHECKED_CAST") req.attributes =
                buildAttributes(data) as @Valid Map<String?, CreateContactRequestAttributesValue?>?
            val response = contactsApi.createContact(req)
            log.info("Created Brevo contact id={} for {}", response.id, data.email)
            response.id!!
        } catch (e: RestClientResponseException) {
            log.error("Failed to create Brevo contact for {}", data.email, e)
            throw ContactServiceException("Failed to create contact", e)
        }
    }

    override fun updateContact(externalId: Long, data: ContactData) {
        log.info("Updating Brevo contact id={}: {}", externalId, data.email)
        try {
            val req = UpdateContactRequest()
            req.extId = data.email
            @Suppress("UNCHECKED_CAST") req.attributes =
                buildAttributes(data) as @Valid Map<String?, CreateContactRequestAttributesValue?>?
            contactsApi.updateContact(data.email, req, "email_id")
            log.info("Updated Brevo contact id={}", externalId)
        } catch (e: RestClientResponseException) {
            log.error("Failed to update Brevo contact id={}", externalId, e)
            throw ContactServiceException("Failed to update contact", e)
        }
    }

    override fun deleteContact(externalId: Long) {
        log.info("Deleting Brevo contact id={}", externalId)
        try {
            contactsApi.deleteContact(externalId.toString(), "contact_id")
        } catch (e: RestClientResponseException) {
            log.error("Failed to delete Brevo contact id={}", externalId, e)
            throw ContactServiceException("Failed to delete contact", e)
        }
    }

    private fun buildAttributes(data: ContactData): Map<String, Any> {
        val attrs = mutableMapOf<String, Any>(
            "NEWSLETTER" to data.newsletter,
            "IS_MEMBER" to data.isMember,
            "FIRSTNAME" to data.firstName,
            "LASTNAME" to data.lastName,
            "SURNAME" to data.lastName
        )
        data.phoneNumber?.let { phone ->
            attrs["SMS"] = phone
            attrs["WHATSAPP"] = phone
        }
        attrs.putAll(data.attributes)
        return attrs
    }

    companion object {
        private val log = LoggerFactory.getLogger(BrevoContactAdapter::class.java)
    }
}
