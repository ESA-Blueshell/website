package net.blueshell.api.contact.api

import net.blueshell.api.contact.domain.ExternalContactGoneException
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
import net.blueshell.api.contact.domain.BrevoApiException
import net.blueshell.api.contact.domain.BrevoDuplicateContactException
import net.blueshell.api.contact.domain.BrevoDuplicateIdentifier
import net.blueshell.api.contact.domain.BrevoError
import net.blueshell.api.contact.domain.parseBrevoError
import net.blueshell.api.contact.domain.DOCUMENT_NOT_FOUND
import net.blueshell.api.contact.domain.DUPLICATE_PARAMETER
import net.blueshell.api.contact.domain.INVALID_PARAMETER

/**
 * Brevo anti-corruption layer for [ContactAdapter] (ADR-019). Active in
 * production only; test/dev use MockContactAdapter. The [ContactsApi] client
 * is wired by [BrevoClientConfig] so this class holds no HTTP setup.
 */
@Service
@Profile("!test & !dev")
class BrevoContactAdapter(
    private val contactsApi: ContactsApi,
    private val jsonMapper: JsonMapper,
) : ContactAdapter {

    override val system = ContactSystem.BREVO

    override fun createContact(data: ContactData): Long = createOrAdopt(data, omittedAttrs = emptySet())

    override fun updateContact(externalId: Long, data: ContactData): Long {
        return try {
            updateById(externalId, data, omittedAttrs = emptySet())
        } catch (e: ExternalContactGoneException) {
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
            contactsApi.deleteContact(externalId.toString(), IDENTIFIER_CONTACT_ID)
        } catch (e: RestClientResponseException) {
            val error = parseBrevoError(e, jsonMapper)
            if (error?.code == DOCUMENT_NOT_FOUND) {
                log.info("Brevo contact {} was already gone on delete", externalId)
                return
            }
            log.error("Failed to delete Brevo contact id={}", externalId, e)
            throw BrevoApiException(e.statusCode.value(), error?.code, error?.message, "deleteContact", e)
        }
    }

    private fun createOrAdopt(data: ContactData, omittedAttrs: Set<String>): Long {
        log.info("Creating Brevo contact: {} (omit={})", data.email, omittedAttrs)
        try {
            val response = contactsApi.createContact(buildCreateRequest(data, omittedAttrs))
            log.info("Created Brevo contact id={} for {}", response.id, data.email)
            return response.id!!
        } catch (e: RestClientResponseException) {
            val error = parseBrevoError(e, jsonMapper)
            return when {
                error?.code == DUPLICATE_PARAMETER -> handleCreateDuplicate(data, omittedAttrs, error, e)
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
     * Decide what to do when Brevo rejected the create with `duplicate_parameter`.
     *
     * We only adopt when the duplicated identifier is `EMAIL`, because email is
     * the stable identity we control: matching emails almost certainly means we
     * are looking at the same person. Phone- or EXT_ID-only duplicates can
     * collide on a completely different contact (a partner / family member
     * sharing a number, or someone whose Brevo `ext_id` happens to match an old
     * email of ours), so attaching there would corrupt the pairing. In that
     * case we drop the conflicting attributes and try creating a fresh contact
     * without them — the rest of the contact still syncs and the colliding
     * data simply isn't pushed.
     */
    private fun handleCreateDuplicate(
        data: ContactData,
        omittedAttrs: Set<String>,
        error: BrevoError,
        cause: RestClientResponseException,
    ): Long {
        val duplicates = error.duplicateIdentifiers.map { BrevoDuplicateIdentifier.from(it) }.toSet()
        if (BrevoDuplicateIdentifier.EMAIL in duplicates) {
            return adoptExistingContact(data, error, cause)
        }
        val expanded = expandOmissions(error.duplicateIdentifiers, omittedAttrs)
        if (expanded.size == omittedAttrs.size) {
            // Brevo reported a duplicate but we don't recognise the identifier,
            // so we can't drop it. Surface the situation rather than loop.
            throw BrevoDuplicateContactException(duplicates, data.email, data.phoneNumber, cause)
        }
        log.warn(
            "Brevo create on {} conflicted on non-email identifier(s) {}; retrying without {}",
            data.email, error.duplicateIdentifiers, expanded - omittedAttrs,
        )
        return createOrAdopt(data, expanded)
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
            lookupContactId(data.email, IDENTIFIER_EMAIL_ID)?.let { return it }
        }
        if (BrevoDuplicateIdentifier.SMS in duplicates) {
            data.phoneNumber?.let { phone -> lookupContactId(phone, IDENTIFIER_PHONE_ID)?.let { return it } }
        }
        // Fall back to email even when Brevo flagged a different field: email
        // is our stable identifier and the most likely correct match.
        return lookupContactId(data.email, IDENTIFIER_EMAIL_ID)
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
                IDENTIFIER_CONTACT_ID,
            )
            log.info("Updated Brevo contact id={}", externalId)
            return externalId
        } catch (e: RestClientResponseException) {
            val error = parseBrevoError(e, jsonMapper)
            return when {
                error?.code == DOCUMENT_NOT_FOUND -> throw ExternalContactGoneException(system, externalId, e)
                error?.code == DUPLICATE_PARAMETER -> {
                    val newOmissions = expandOmissions(error.duplicateIdentifiers, omittedAttrs)
                    if (newOmissions.size == omittedAttrs.size) {
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

    // The generated models are immutable Kotlin data classes, so these are
    // built through the constructor rather than mutated after construction.
    //
    // The attributes cast stays unchecked: Brevo types every attribute value as
    // a `oneOf` the generator renders as CreateContactRequestAttributesValue,
    // but the wire form is a plain scalar, and Jackson serialises the map by
    // each value's runtime type. Constructing a wrapper per attribute would
    // produce the same JSON through more code.
    private fun buildCreateRequest(data: ContactData, omittedAttrs: Set<String>): CreateContactRequest {
        @Suppress("UNCHECKED_CAST")
        return CreateContactRequest(
            email = data.email,
            extId = data.email.takeIf { ATTR_EXT_ID !in omittedAttrs },
            attributes = buildAttributes(data, omittedAttrs) as Map<String, CreateContactRequestAttributesValue>,
        )
    }

    private fun buildUpdateRequest(data: ContactData, omittedAttrs: Set<String>): UpdateContactRequest {
        @Suppress("UNCHECKED_CAST")
        return UpdateContactRequest(
            extId = data.email.takeIf { ATTR_EXT_ID !in omittedAttrs },
            attributes = buildAttributes(data, omittedAttrs) as Map<String, CreateContactRequestAttributesValue>,
        )
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
            if (ATTR_SMS !in omittedAttrs) attrs[ATTR_SMS] = phone
            if (ATTR_WHATSAPP !in omittedAttrs) attrs[ATTR_WHATSAPP] = phone
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

        // Brevo `identifierType` values, see ContactsApi javadoc.
        private const val IDENTIFIER_CONTACT_ID = "contact_id"
        private const val IDENTIFIER_EMAIL_ID = "email_id"
        private const val IDENTIFIER_PHONE_ID = "phone_id"

        // Brevo attribute keys we set on create/update.
        private const val ATTR_SMS = "SMS"
        private const val ATTR_WHATSAPP = "WHATSAPP"
        private const val ATTR_EXT_ID = "EXT_ID"
        private val PHONE_ATTRS = setOf(ATTR_SMS, ATTR_WHATSAPP)
    }
}
