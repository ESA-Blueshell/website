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
 * Implements [ContactAdapter] against the Brevo Contacts API. Active in
 * production only (test/dev use MockContactAdapter). The [ContactsApi] client
 * is wired by [BrevoClientConfig] so this class stays free of HTTP setup and
 * can be unit-tested with a mock client.
 *
 * Recovery semantics:
 * - Update routes by `contact_id`, never by `email_id`, so the adopt-then-update
 *   path acts on the contact we actually resolved.
 * - A `document_not_found` on update means the stored Brevo id is stale; we
 *   transparently recreate (which may adopt a different existing contact) and
 *   return the new id so the orchestration layer repairs the mapping.
 * - When Brevo rejects a write because a specific identifier (`SMS`,
 *   `WHATSAPP`, `EXT_ID`) is already taken or invalid (typically a malformed
 *   phone), we retry the same write with that attribute dropped, so the rest
 *   of the contact still syncs.
 */
@Service
@Profile("!test & !dev")
class BrevoContactAdapter(
    private val contactsApi: ContactsApi,
    private val jsonMapper: JsonMapper,
) : ContactAdapter {

    override val system = ContactSystem.BREVO

    // ── Contact operations ─────────────────────────────────────────────────────

    override fun createContact(data: ContactData): Long = createOrAdopt(data, omittedAttrs = emptySet())

    override fun updateContact(externalId: Long, data: ContactData): Long {
        return try {
            updateById(externalId, data, omittedAttrs = emptySet())
        } catch (e: BrevoContactGoneException) {
            // Stale local mapping: the Brevo contact was deleted or merged. Fall
            // through to create-or-adopt so the next external id replaces the
            // dead one in external_id_mapping.
            log.warn("Brevo contact {} is gone; repairing pairing by re-creating", externalId, e)
            createOrAdopt(data, omittedAttrs = emptySet())
        }
    }

    override fun deleteContact(externalId: Long) {
        log.info("Deleting Brevo contact id={}", externalId)
        try {
            contactsApi.deleteContact(externalId.toString(), "contact_id")
        } catch (e: RestClientResponseException) {
            val error = parseBrevoError(e, jsonMapper)
            if (error?.code == DOCUMENT_NOT_FOUND) {
                // Already gone — treat as a successful delete.
                log.info("Brevo contact {} was already gone on delete", externalId)
                return
            }
            log.error("Failed to delete Brevo contact id={}", externalId, e)
            throw BrevoApiException(e.statusCode.value(), error?.code, error?.message, "deleteContact", e)
        }
    }

    // ── Internal helpers ──────────────────────────────────────────────────────

    private fun createOrAdopt(data: ContactData, omittedAttrs: Set<String>): Long {
        log.info("Creating Brevo contact: {} (omit={})", data.email, omittedAttrs)
        try {
            val response = contactsApi.createContact(buildCreateRequest(data, omittedAttrs))
            log.info("Created Brevo contact id={} for {}", response.id, data.email)
            return response.id!!
        } catch (e: RestClientResponseException) {
            val error = parseBrevoError(e, jsonMapper)
            return when {
                error?.code == DUPLICATE_PARAMETER -> adoptExistingContact(data, error, e)
                shouldDropPhone(error, omittedAttrs) -> {
                    log.warn("Brevo create rejected phone for {}; retrying without SMS/WHATSAPP", data.email)
                    createOrAdopt(data, omittedAttrs + PHONE_ATTRS)
                }
                else -> {
                    log.error("Failed to create Brevo contact for {}", data.email, e)
                    throw BrevoApiException(e.statusCode.value(), error?.code, error?.message, "createContact", e)
                }
            }
        }
    }

    /**
     * Brevo rejected a create because the contact already exists. Look the
     * existing contact up by the duplicated identifier and push the intended
     * attributes onto it, returning that id so the orchestration layer adopts
     * the pairing.
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
        // Push the intended attributes onto the resolved contact. updateById
        // updates by contact_id (so we hit the contact we actually resolved)
        // and falls through on conflicting / invalid attributes.
        updateById(existingId, data, omittedAttrs = emptySet())
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

    /**
     * Updates by `contact_id` and retries with the conflicting / invalid
     * attributes dropped. Returns the same id on success; throws
     * [BrevoContactGoneException] if Brevo says the contact does not exist.
     */
    private fun updateById(externalId: Long, data: ContactData, omittedAttrs: Set<String>): Long {
        log.info("Updating Brevo contact id={}: {} (omit={})", externalId, data.email, omittedAttrs)
        try {
            contactsApi.updateContact(
                externalId.toString(),
                buildUpdateRequest(data, omittedAttrs),
                "contact_id",
            )
            log.info("Updated Brevo contact id={}", externalId)
            return externalId
        } catch (e: RestClientResponseException) {
            val error = parseBrevoError(e, jsonMapper)
            return when {
                error?.code == DOCUMENT_NOT_FOUND -> throw BrevoContactGoneException(externalId, e)
                error?.code == DUPLICATE_PARAMETER -> {
                    val newOmissions = expandOmissions(error.duplicateIdentifiers, omittedAttrs)
                    if (newOmissions.size == omittedAttrs.size) {
                        // Nothing new to drop; can't recover. Surface as a typed error.
                        log.error("Brevo update on {} conflicted but no attribute can be dropped: {}", externalId, error)
                        throw BrevoApiException(e.statusCode.value(), error.code, error.message, "updateContact", e)
                    }
                    log.warn(
                        "Brevo update on {} conflicted on {}; retrying without those attrs",
                        externalId, newOmissions - omittedAttrs,
                    )
                    updateById(externalId, data, newOmissions)
                }
                shouldDropPhone(error, omittedAttrs) -> {
                    log.warn("Brevo update on {} rejected phone; retrying without SMS/WHATSAPP", externalId)
                    updateById(externalId, data, omittedAttrs + PHONE_ATTRS)
                }
                else -> {
                    log.error("Failed to update Brevo contact id={}", externalId, e)
                    throw BrevoApiException(e.statusCode.value(), error?.code, error?.message, "updateContact", e)
                }
            }
        }
    }

    private fun buildCreateRequest(data: ContactData, omittedAttrs: Set<String>): CreateContactRequest {
        val req = CreateContactRequest()
        req.email = data.email
        if ("EXT_ID" !in omittedAttrs) req.extId = data.email   // Brevo extId used for dedup
        @Suppress("UNCHECKED_CAST")
        req.attributes = buildAttributes(data, omittedAttrs) as Map<String?, CreateContactRequestAttributesValue?>?
        return req
    }

    private fun buildUpdateRequest(data: ContactData, omittedAttrs: Set<String>): UpdateContactRequest {
        val req = UpdateContactRequest()
        if ("EXT_ID" !in omittedAttrs) req.extId = data.email
        @Suppress("UNCHECKED_CAST")
        req.attributes = buildAttributes(data, omittedAttrs) as Map<String?, CreateContactRequestAttributesValue?>?
        return req
    }

    private fun buildAttributes(data: ContactData, omittedAttrs: Set<String>): Map<String, Any> {
        val attrs = mutableMapOf<String, Any>(
            "NEWSLETTER" to data.newsletter,
            "IS_MEMBER" to data.isMember,
            "FIRSTNAME" to data.firstName,
            "LASTNAME" to data.lastName,
            "SURNAME" to data.lastName,
        )
        data.phoneNumber?.let { phone ->
            if ("SMS" !in omittedAttrs) attrs["SMS"] = phone
            if ("WHATSAPP" !in omittedAttrs) attrs["WHATSAPP"] = phone
        }
        attrs.putAll(data.attributes.filterKeys { it.uppercase() !in omittedAttrs })
        return attrs
    }

    /**
     * Expands Brevo's `duplicate_identifiers` into the set of attributes we
     * should omit. SMS and WHATSAPP carry the same domain value (the phone), so
     * a conflict on either implies we should drop both.
     */
    private fun expandOmissions(duplicateIdentifiers: List<String>, already: Set<String>): Set<String> {
        val upper = duplicateIdentifiers.map { it.uppercase() }.toMutableSet()
        if (upper.intersect(PHONE_ATTRS).isNotEmpty()) upper.addAll(PHONE_ATTRS)
        return already + upper
    }

    private fun shouldDropPhone(error: BrevoError?, omittedAttrs: Set<String>): Boolean {
        if (error?.code != INVALID_PARAMETER) return false
        val message = error.message?.lowercase() ?: return false
        if (!message.contains("phone")) return false
        return !omittedAttrs.containsAll(PHONE_ATTRS)
    }

    companion object {
        private val log = LoggerFactory.getLogger(BrevoContactAdapter::class.java)
        private val PHONE_ATTRS = setOf("SMS", "WHATSAPP")
    }
}
