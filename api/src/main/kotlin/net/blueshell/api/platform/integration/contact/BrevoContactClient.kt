package net.blueshell.api.platform.integration.contact

import net.blueshell.clients.brevo.api.ContactsApi
import net.blueshell.clients.brevo.model.*
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.http.HttpStatus
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientResponseException

/**
 * Low-level Brevo Contact API client.
 *
 * This is NOT an Anti-Corruption Layer - it's a thin wrapper around Brevo's API.
 * The ACL is implemented in BrevoContactAdapter, which translates between
 * domain concepts and this client's Brevo-specific operations.
 */
@Component
class BrevoContactClient(
    private val restClientBuilder: RestClient.Builder,
    @param:Value("\${brevo.apiKey}") private val apiKey: String,
    @param:Value("\${brevo.baseUrl:https://api.brevo.com/v3}") private val brevoBaseUrl: String,
    @param:Value("\${brevo.folders.contributionPeriodsId}") private val contributionPeriodsFolder: Long
) {
    private val contactsApi: ContactsApi
        get() {
            val client = restClientBuilder
                .baseUrl(brevoBaseUrl)
                .defaultHeader("api-key", apiKey)
                .build()
            return ContactsApi(client)
        }

    /**
     * Get contact information by email.
     * Returns the contact ID if found, null otherwise.
     */
    fun getContactIdByEmail(email: String): Long? {
        return try {
            val details = contactsApi.getContactInfo(email, "email_id", null, null)
            log.debug("Found Brevo contact for email {}: contactId={}", email, details.id)
            details.id
        } catch (e: HttpClientErrorException) {
            if (e.statusCode != HttpStatus.NOT_FOUND) {
                log.error("Brevo lookup failed for email {} with status {}", email, e.statusCode, e)
                throw e
            }
            log.debug("Brevo contact not found for email: {}", email)
            null
        }
    }

    /**
     * Create a new contact in Brevo.
     * Returns the created contact ID.
     */
    @Throws(RestClientResponseException::class)
    fun createContact(
        email: String,
        externalId: String,
        attributes: Map<String, Any>
    ): Long {
        log.info("Creating Brevo contact for email: {}", email)
        val createContact = CreateContact().apply {
            this.email = email
            this.extId = externalId
            this.attributes = attributes.toMutableMap()
        }
        val response = contactsApi.createContact(createContact)
        return response.id!!
    }

    /**
     * Update an existing contact in Brevo.
     */
    @Throws(RestClientResponseException::class)
    fun updateContact(
        email: String,
        externalId: String,
        attributes: Map<String, Any>
    ) {
        log.info("Updating Brevo contact for email: {}", email)
        val updateContact = UpdateContact().apply {
            this.extId = externalId
            this.attributes = attributes.toMutableMap()
        }
        contactsApi.updateContact(email, updateContact, "email_id")
    }

    /**
     * Create a new contact list in Brevo.
     * Returns the created list ID.
     */
    @Throws(RestClientResponseException::class)
    fun createList(listName: String, folderId: Long): Long {
        log.info("Creating Brevo list: {}", listName)
        val createList = CreateList(listName, folderId)
        val response = contactsApi.createList(createList)
        return response.id
    }

    /**
     * Add contacts to a list in Brevo.
     */
    @Throws(RestClientResponseException::class)
    fun addContactsToList(listId: Long, contactIds: List<Long>) {
        log.info("Adding {} contacts to Brevo list {}", contactIds.size, listId)
        val payload = AddContactToListRequest().apply {
            this.ids = contactIds.toMutableList()
        }
        contactsApi.addContactToList(listId, payload)
    }

    /**
     * Remove contacts from a list in Brevo.
     */
    @Throws(RestClientResponseException::class)
    fun removeContactsFromList(listId: Long, contactIds: List<Long>) {
        log.info("Removing {} contacts from Brevo list {}", contactIds.size, listId)
        val payload = RemoveContactFromListRequest().apply {
            this.ids = contactIds.toMutableList()
        }
        contactsApi.removeContactFromList(listId, payload)
    }

    /**
     * Get the folder ID for contribution periods.
     */
    fun getContributionPeriodsFolderId(): Long {
        return contributionPeriodsFolder
    }

    companion object {
        private val log = LoggerFactory.getLogger(BrevoContactClient::class.java)
    }
}
