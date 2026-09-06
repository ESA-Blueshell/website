package net.blueshell.api.contact.domain

import net.blueshell.api.contact.api.ContactListMember
import net.blueshell.api.contact.api.ContactListRef
import net.blueshell.api.contact.api.ContactServiceException
import net.blueshell.api.contact.api.ContactListAdapter
import net.blueshell.api.shared.enums.TargetSystem
import net.blueshell.clients.brevo.api.ContactsApi
import net.blueshell.clients.brevo.model.AddContactToListRequest
import net.blueshell.clients.brevo.model.CreateListRequest
import net.blueshell.clients.brevo.model.UpdateListRequest
import net.blueshell.clients.brevo.model.GetContactsSortParameter
import net.blueshell.clients.brevo.model.RemoveContactFromListRequest
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClientResponseException
import tools.jackson.databind.json.JsonMapper

/**
 * Brevo anti-corruption layer for [ContactListAdapter] (ADR-019), in production only.
 *
 * [contributionPeriodsFolder] is a numeric Brevo folder id: Brevo organises lists by id rather
 * than name, so the domain's `folderName` hint is ignored.
 *
 * Brevo answers an add for a contact already in the list with an ambiguous
 * `400 invalid_parameter "Contact already in list and/or does not exist"`, so a follow-up GET
 * disambiguates: 200 makes the add an idempotent success, 404 means the local pairing is stale
 * and raises [BrevoContactGoneException] for the caller to re-sync. Removing a contact that is
 * not in the list is a no-op.
 */
@Service
@Profile("!test & !dev")
class BrevoListAdapter(
    private val contactsApi: ContactsApi,
    private val jsonMapper: JsonMapper,
    @param:Value($$"${brevo.folders.contributionPeriodsId}") private val contributionPeriodsFolder: Long,
) : ContactListAdapter {

    override val system = TargetSystem.BREVO

    override fun moveList(externalListId: Long, folderId: Long) {
        log.info("Moving Brevo list {} to folder {}", externalListId, folderId)
        try {
            // Brevo accepts a name or a folder in one call but not both, so this only moves.
            contactsApi.updateList(externalListId, UpdateListRequest(folderId = folderId))
        } catch (e: RestClientResponseException) {
            log.error("Failed to move Brevo list {} to folder {}", externalListId, folderId, e)
            throw ContactServiceException("Failed to move list", e)
        }
    }

    override fun listFolders(): Map<Long, String> =
        page("folders") { limit, offset ->
            contactsApi.getFolders(limit, offset, GetContactsSortParameter.ASC)
                .let { Page(it.count, it.folders.orEmpty().map { folder -> folder.id to folder.name }) }
        }.toMap()

    override fun listAll(): List<ContactListRef> =
        page("lists") { limit, offset ->
            contactsApi.getLists(limit, offset, GetContactsSortParameter.ASC).let { page ->
                Page(
                    page.count,
                    page.lists.orEmpty().map {
                        ContactListRef(
                            externalListId = it.id,
                            name = it.name,
                            folderId = it.folderId,
                            memberCount = it.uniqueSubscribers,
                        )
                    },
                )
            }
        }

    /**
     * Walks a Brevo collection until a short page or the reported count ends it. A rate-limited
     * page is logged as such and raised like any other fetch failure, so the job retries.
     */
    private fun <T> page(kind: String, fetch: (Long, Long) -> Page<T>): List<T> {
        val results = mutableListOf<T>()
        var offset = 0L
        while (true) {
            val page = try {
                fetch(CATALOG_PAGE_SIZE, offset)
            } catch (e: RestClientResponseException) {
                if (e.statusCode.value() == 429) log.warn("Brevo {} fetch was rate limited", kind)
                log.error("Failed to read Brevo {}", kind, e)
                throw ContactServiceException("Failed to read $kind", e)
            }
            results += page.items
            if (page.items.size < CATALOG_PAGE_SIZE || page.count != null && results.size >= page.count) break
            offset += CATALOG_PAGE_SIZE
        }
        return results
    }

    private data class Page<T>(val count: Long?, val items: List<T>)

    override fun createList(name: String, folderName: String?): Long {
        val safeName = sanitizeForLog(name)
        log.info("Creating Brevo list '{}'", safeName)
        return try {
            val response = contactsApi.createList(
                CreateListRequest(name = name, folderId = contributionPeriodsFolder),
            )
            log.info("Created Brevo list '{}' id={}", safeName, response.id)
            response.id
        } catch (e: RestClientResponseException) {
            log.error("Failed to create Brevo list '{}'", safeName, e)
            throw ContactServiceException("Failed to create list", e)
        }
    }

    private fun sanitizeForLog(value: String): String = buildString(value.length) {
        value.forEach { ch ->
            append(if (ch.isISOControl()) '_' else ch)
        }
    }

    override fun addToList(externalUserId: Long, externalListId: Long) {
        log.info("Adding Brevo contact {} to list {}", externalUserId, externalListId)
        try {
            contactsApi.addContactToList(externalListId, AddContactToListRequest(ids = listOf(externalUserId)))
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
            contactsApi.removeContactFromList(
                externalListId,
                RemoveContactFromListRequest(ids = listOf(externalUserId)),
            )
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
            val contacts = page.contacts
            for (c in contacts) {
                val id = c.id
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
        /** Brevo's own page cap for folders and lists. */
        private const val CATALOG_PAGE_SIZE = 50L
        private val log = LoggerFactory.getLogger(BrevoListAdapter::class.java)
    }
}
