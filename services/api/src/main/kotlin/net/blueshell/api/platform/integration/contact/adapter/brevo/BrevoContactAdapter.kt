package net.blueshell.api.platform.integration.contact.adapter.brevo

import net.blueshell.api.platform.integration.contact.adapter.ContactAdapter
import net.blueshell.api.platform.integration.contact.adapter.ContactData
import net.blueshell.api.shared.enums.ContactSystem
import net.blueshell.clients.brevo.api.ContactsApi
import net.blueshell.clients.brevo.model.CreateContactRequest
import net.blueshell.clients.brevo.model.CreateContactRequestAttributesValue
import net.blueshell.clients.brevo.model.UpdateContactRequest
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClientResponseException
import tools.jackson.databind.json.JsonMapper

/**
 * Brevo Anti-Corruption Layer (ADR-019)
 *
 * Implements [ContactAdapter] against the Brevo Contacts API.
 * Active in production only (test/dev use MockContactAdapter). The [ContactsApi]
 * client is wired by [BrevoClientConfig] so this class stays free of HTTP setup
 * and can be unit-tested with a mock client.
 */
@Service
@Profile("!test & !dev")
class BrevoContactAdapter(
    private val contactsApi: ContactsApi,
    private val jsonMapper: JsonMapper,
) : ContactAdapter {

    override val system = ContactSystem.BREVO

    // ── Contact operations ─────────────────────────────────────────────────────

    override fun createContact(data: ContactData): Long {
        log.info("Creating Brevo contact: {}", data.email)
        return try {
            val req = CreateContactRequest()
            req.email = data.email
            req.extId = data.email   // Brevo extId used for dedup
            @Suppress("UNCHECKED_CAST")
            val createAttrs = buildAttributes(data) as Map<String?, CreateContactRequestAttributesValue?>?
            req.attributes = createAttrs
            val response = contactsApi.createContact(req)
            log.info("Created Brevo contact id={} for {}", response.id, data.email)
            response.id!!
        } catch (e: RestClientResponseException) {
            val error = parseBrevoError(e)
            if (error?.code == DUPLICATE_PARAMETER) {
                return adoptExistingContact(data, error, e)
            }
            log.error("Failed to create Brevo contact for {}", data.email, e)
            throw BrevoApiException(e.statusCode.value(), error?.code, error?.message, "createContact", e)
        }
    }

    override fun updateContact(externalId: Long, data: ContactData) {
        log.info("Updating Brevo contact id={}: {}", externalId, data.email)
        try {
            val req = UpdateContactRequest()
            req.extId = data.email
            @Suppress("UNCHECKED_CAST")
            val updateAttrs = buildAttributes(data) as Map<String?, CreateContactRequestAttributesValue?>?
            req.attributes = updateAttrs
            contactsApi.updateContact(data.email, req, "email_id")
            log.info("Updated Brevo contact id={}", externalId)
        } catch (e: RestClientResponseException) {
            log.error("Failed to update Brevo contact id={}", externalId, e)
            val error = parseBrevoError(e)
            throw BrevoApiException(e.statusCode.value(), error?.code, error?.message, "updateContact", e)
        }
    }

    override fun deleteContact(externalId: Long) {
        log.info("Deleting Brevo contact id={}", externalId)
        try {
            contactsApi.deleteContact(externalId.toString(), "contact_id")
        } catch (e: RestClientResponseException) {
            log.error("Failed to delete Brevo contact id={}", externalId, e)
            val error = parseBrevoError(e)
            throw BrevoApiException(e.statusCode.value(), error?.code, error?.message, "deleteContact", e)
        }
    }

    /**
     * Brevo rejected the create because the contact already exists. Look the
     * existing contact up by the duplicated identifier, push the intended
     * attributes onto it, and adopt its id so the sync records the mapping and
     * future runs take the update path.
     */
    private fun adoptExistingContact(
        data: ContactData,
        error: BrevoError,
        cause: RestClientResponseException,
    ): Long {
        val duplicates = error.duplicateIdentifiers.map { BrevoDuplicateIdentifier.from(it) }.toSet()
        log.warn("Brevo reports duplicate {} for {}; adopting existing contact", duplicates, data.email)
        val existingId = resolveExistingId(data, duplicates)
            ?: throw BrevoDuplicateContactException(duplicates, data.email, data.phoneNumber, cause)
        updateContact(existingId, data)
        log.info("Adopted existing Brevo contact id={} for {}", existingId, data.email)
        return existingId
    }

    private fun resolveExistingId(data: ContactData, duplicates: Set<BrevoDuplicateIdentifier>): Long? {
        if (BrevoDuplicateIdentifier.EMAIL in duplicates) {
            lookupContactId(data.email, "email_id")?.let { return it }
        }
        if (BrevoDuplicateIdentifier.SMS in duplicates) {
            data.phoneNumber?.let { phone -> lookupContactId(phone, "phone_id")?.let { return it } }
        }
        // Best-effort fallback: the email is our stable identifier, try it even
        // when Brevo flagged a different (or unknown) field.
        return lookupContactId(data.email, "email_id")
    }

    private fun lookupContactId(identifier: String, identifierType: String): Long? =
        try {
            contactsApi.getContactInfo(identifier, identifierType, null, null).id
        } catch (e: RestClientResponseException) {
            log.warn("Brevo lookup by {}={} failed: {}", identifierType, identifier, e.statusCode)
            null
        }

    private fun parseBrevoError(e: RestClientResponseException): BrevoError? {
        val body = e.responseBodyAsString.takeIf { it.isNotBlank() } ?: return null
        return try {
            val map = jsonMapper.readValue(body, Map::class.java)
            val metadata = map["metadata"] as? Map<*, *>
            val ids = (metadata?.get("duplicate_identifiers") as? List<*>)
                ?.mapNotNull { it as? String }
                ?: emptyList()
            BrevoError(
                code = map["code"] as? String,
                message = map["message"] as? String,
                duplicateIdentifiers = ids,
            )
        } catch (ex: Exception) {
            log.warn("Could not parse Brevo error body: {}", body, ex)
            null
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

    /** Parsed shape of a Brevo error response body. */
    private data class BrevoError(
        val code: String?,
        val message: String?,
        val duplicateIdentifiers: List<String>,
    )

    companion object {
        private val log = LoggerFactory.getLogger(BrevoContactAdapter::class.java)
        private const val DUPLICATE_PARAMETER = "duplicate_parameter"
    }
}
