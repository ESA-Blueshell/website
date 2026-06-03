package net.blueshell.api.platform.integration.contact.adapter.brevo

import net.blueshell.api.platform.integration.contact.adapter.ContactListMember
import net.blueshell.api.platform.integration.contact.adapter.ContactServiceException
import net.blueshell.api.platform.integration.contact.adapter.ContactListAdapter
import net.blueshell.api.platform.integration.contact.adapter.ExternalContactGoneException
import net.blueshell.api.shared.enums.ContactSystem
import net.blueshell.clients.brevo.api.ContactsApi
import net.blueshell.clients.brevo.model.AddContactToListRequest
import net.blueshell.clients.brevo.model.CreateListRequest
import net.blueshell.clients.brevo.model.RemoveContactFromListRequest
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClientResponseException
import tools.jackson.databind.json.JsonMapper

/**
 * Brevo Anti-Corruption Layer (ADR-019)
 *
 * Implements [ContactListAdapter] against the Brevo Contacts API. Active in
 * production only (test/dev use MockContactAdapter).
 *
 * [contributionPeriodsFolder] is the Brevo folder ID under which all
 * contribution-period lists are created; the domain-level `folderName` hint is
 * intentionally ignored because Brevo organises lists by numeric folder ID,
 * not by name.
 *
 * Recovery semantics:
 * - Adding a contact already in the list returns Brevo's ambiguous
 *   `400 invalid_parameter "Contact already in list and/or does not exist"`.
 *   We disambiguate with a follow-up GET on the contact id: a 200 means the
 *   contact already belongs and the add is treated as an idempotent success;
 *   a 404 means the local pairing is stale and we throw
 *   [BrevoContactGoneException] so the caller can re-sync and retry.
 * - Removing a contact that isn't in the list is treated as a no-op (the
 *   desired state is already in place).
 */
@Service
@Profile("!test & !dev")
class BrevoListAdapter(
    private val contactsApi: ContactsApi,
    private val jsonMapper: JsonMapper,
    @param:Value($$"${brevo.folders.contributionPeriodsId}") private val contributionPeriodsFolder: Long,
) : ContactListAdapter {

    override val system = ContactSystem.BREVO

    override fun createList(name: String, folderName: String?): Long {
        log.info("Creating Brevo list '{}'", name)
        return try {
            val req = CreateListRequest()
            req.name = name
            req.folderId = contributionPeriodsFolder
            val response = contactsApi.createList(req)
            log.info("Created Brevo list '{}' id={}", name, response.id)
            response.id
        } catch (e: RestClientResponseException) {
            log.error("Failed to create Brevo list '{}'", name, e)
            throw ContactServiceException("Failed to create list", e)
        }
    }

    override fun addToList(externalUserId: Long, externalListId: Long) {
        log.info("Adding Brevo contact {} to list {}", externalUserId, externalListId)
        try {
            val req = AddContactToListRequest()
            req.ids = mutableListOf(externalUserId)
            req.emails = null
            req.extIds = null
            contactsApi.addContactToList(externalListId, req)
        } catch (e: RestClientResponseException) {
            val error = parseBrevoError(e, jsonMapper)
            if (error?.code == INVALID_PARAMETER && isAlreadyInListOrMissing(error)) {
                when (lookupContact(externalUserId)) {
                    ContactLookup.EXISTS -> {
                        log.info(
                            "Brevo contact {} already in list {} — treating add as a no-op",
                            externalUserId, externalListId,
                        )
                        return
                    }
                    ContactLookup.MISSING -> {
                        log.warn(
                            "Brevo says contact {} does not exist while adding to list {}",
                            externalUserId, externalListId,
                        )
                        throw ExternalContactGoneException(system, externalUserId, e)
                    }
                    ContactLookup.UNKNOWN -> {
                        // Lookup itself failed (transient 5xx, 429, network).
                        // Don't churn local pairing for a provider outage; let
                        // the job retry the whole add via its backoff schedule.
                        log.warn(
                            "Brevo contact existence check inconclusive for {} — letting the job retry",
                            externalUserId,
                        )
                        throw ContactServiceException("Failed to add contact to list", e)
                    }
                }
            }
            log.error("Failed to add contact {} to Brevo list {}", externalUserId, externalListId, e)
            throw ContactServiceException("Failed to add contact to list", e)
        }
    }

    override fun removeFromList(externalUserId: Long, externalListId: Long) {
        log.info("Removing Brevo contact {} from list {}", externalUserId, externalListId)
        try {
            val req = RemoveContactFromListRequest()
            req.ids = mutableListOf(externalUserId)
            req.emails = null
            req.extIds = null
            contactsApi.removeContactFromList(externalListId, req)
        } catch (e: RestClientResponseException) {
            val error = parseBrevoError(e, jsonMapper)
            if (error?.code == INVALID_PARAMETER && isAlreadyInListOrMissing(error)) {
                log.info(
                    "Brevo contact {} not in list {} (or already deleted) — treating remove as a no-op",
                    externalUserId, externalListId,
                )
                return
            }
            log.error("Failed to remove contact {} from Brevo list {}", externalUserId, externalListId, e)
            throw ContactServiceException("Failed to remove contact from list", e)
        }
    }

    override fun deleteList(externalListId: Long) {
        log.info("Deleting Brevo list id={}", externalListId)
        try {
            contactsApi.deleteList(externalListId)
        } catch (e: RestClientResponseException) {
            log.error("Failed to delete Brevo list id={}", externalListId, e)
            throw ContactServiceException("Failed to delete list", e)
        }
    }

    /**
     * Pages [contactsApi.getContactsFromList] in batches of 500 until a
     * short page signals the end. De-duplicates by external user id across
     * pages in case of cursor drift. Never returns more than ~2 000 rows.
     */
    override fun listMembers(externalListId: Long): List<ContactListMember> {
        val pageSize = 500L
        val seen = LinkedHashSet<Long>()
        val result = mutableListOf<ContactListMember>()
        var offset = 0L
        while (true) {
            val page = try {
                contactsApi.getContactsFromList(externalListId, null, pageSize, offset, null)
            } catch (e: RestClientResponseException) {
                log.error("Failed to list members of Brevo list id={}", externalListId, e)
                throw ContactServiceException("Failed to list members", e)
            }
            val contacts = page.contacts ?: break
            for (c in contacts) {
                val id = c.id ?: continue
                if (seen.add(id)) {
                    result += ContactListMember(id, c.email)
                }
            }
            if (contacts.size < pageSize) break
            offset += pageSize
        }
        return result
    }

    /**
     * Result of disambiguating Brevo's "already in list and/or does not exist"
     * error. Only a confirmed 404 / `document_not_found` is treated as MISSING;
     * any other lookup failure stays UNKNOWN so callers don't repair pairing on
     * a provider outage.
     */
    private enum class ContactLookup { EXISTS, MISSING, UNKNOWN }

    private fun lookupContact(contactId: Long): ContactLookup = try {
        contactsApi.getContactInfo(contactId.toString(), "contact_id", null, null)
        ContactLookup.EXISTS
    } catch (e: RestClientResponseException) {
        val error = parseBrevoError(e, jsonMapper)
        if (e.statusCode.value() == 404 || error?.code == DOCUMENT_NOT_FOUND) {
            ContactLookup.MISSING
        } else {
            ContactLookup.UNKNOWN
        }
    }

    private fun isAlreadyInListOrMissing(error: BrevoError): Boolean {
        val message = error.message?.lowercase() ?: return false
        return message.contains("already in list") || message.contains("does not exist")
    }

    companion object {
        private val log = LoggerFactory.getLogger(BrevoListAdapter::class.java)
    }
}
