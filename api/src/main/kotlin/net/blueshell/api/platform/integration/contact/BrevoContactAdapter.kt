package net.blueshell.api.platform.integration.contact

import net.blueshell.api.domain.user.application.contact.ContactData
import net.blueshell.api.domain.user.application.contact.ContactServiceException
import net.blueshell.api.domain.user.application.contact.ContactSyncAdapter
import net.blueshell.api.domain.user.application.contact.ContactSystem
import net.blueshell.api.domain.user.application.contact.ListSyncAdapter
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClientResponseException

/**
 * Brevo Contact Anti-Corruption Layer (ADR-019)
 *
 * Implements [ContactSyncAdapter] and [ListSyncAdapter] against the Brevo Contact API.
 * Active in production profile only (test/dev use MockContactAdapter).
 *
 * All IDs are system-specific Longs. The orchestration services resolve domain IDs to these
 * system IDs before invoking adapter methods.
 */
@Service
@Profile("!test & !dev")
class BrevoContactAdapter(
    private val brevoClient: BrevoContactClient
) : ContactSyncAdapter, ListSyncAdapter {

    override val system = ContactSystem.BREVO

    // ── ContactSyncAdapter ────────────────────────────────────────────────────

    override fun createContact(data: ContactData): Long {
        log.info("Creating Brevo contact: {}", data.email)
        return try {
            val createdId = brevoClient.createContact(
                email = data.email,
                externalId = data.email,   // Brevo extId used for dedup
                attributes = buildAttributes(data)
            )
            log.info("Created Brevo contact id={} for {}", createdId, data.email)
            createdId
        } catch (e: RestClientResponseException) {
            log.error("Failed to create Brevo contact for {}", data.email, e)
            throw ContactServiceException("Failed to create contact", e)
        }
    }

    override fun updateContact(systemContactId: Long, data: ContactData) {
        log.info("Updating Brevo contact id={}: {}", systemContactId, data.email)
        try {
            brevoClient.updateContact(
                email = data.email,
                externalId = data.email,
                attributes = buildAttributes(data)
            )
            log.info("Updated Brevo contact id={}", systemContactId)
        } catch (e: RestClientResponseException) {
            log.error("Failed to update Brevo contact id={}", systemContactId, e)
            throw ContactServiceException("Failed to update contact", e)
        }
    }

    override fun deleteContact(systemContactId: Long) {
        log.info("Deleting Brevo contact id={}", systemContactId)
        try {
            brevoClient.deleteContact(systemContactId)
        } catch (e: RestClientResponseException) {
            log.error("Failed to delete Brevo contact id={}", systemContactId, e)
            throw ContactServiceException("Failed to delete contact", e)
        }
    }

    // ── ListSyncAdapter ───────────────────────────────────────────────────────

    override fun createList(name: String, folderName: String?): Long {
        log.info("Creating Brevo list '{}' in folder '{}'", name, folderName)
        return try {
            val folderId = when (folderName) {
                "contributionPeriods" -> brevoClient.getContributionPeriodsFolderId()
                else -> throw ContactServiceException("Unknown Brevo folder: $folderName")
            }
            val listId = brevoClient.createList(name, folderId)
            log.info("Created Brevo list '{}' id={}", name, listId)
            listId
        } catch (e: RestClientResponseException) {
            log.error("Failed to create Brevo list '{}'", name, e)
            throw ContactServiceException("Failed to create list", e)
        }
    }

    override fun addToList(systemContactId: Long, systemListId: Long) {
        log.info("Adding Brevo contact {} to list {}", systemContactId, systemListId)
        try {
            brevoClient.addContactsToList(systemListId, listOf(systemContactId))
        } catch (e: RestClientResponseException) {
            log.error("Failed to add contact {} to list {}", systemContactId, systemListId, e)
            throw ContactServiceException("Failed to add contact to list", e)
        }
    }

    override fun removeFromList(systemContactId: Long, systemListId: Long) {
        log.info("Removing Brevo contact {} from list {}", systemContactId, systemListId)
        try {
            brevoClient.removeContactsFromList(systemListId, listOf(systemContactId))
        } catch (e: RestClientResponseException) {
            log.error("Failed to remove contact {} from list {}", systemContactId, systemListId, e)
            throw ContactServiceException("Failed to remove contact from list", e)
        }
    }

    override fun deleteList(systemListId: Long) {
        log.info("Deleting Brevo list id={}", systemListId)
        try {
            brevoClient.deleteList(systemListId)
        } catch (e: RestClientResponseException) {
            log.error("Failed to delete Brevo list id={}", systemListId, e)
            throw ContactServiceException("Failed to delete list", e)
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
