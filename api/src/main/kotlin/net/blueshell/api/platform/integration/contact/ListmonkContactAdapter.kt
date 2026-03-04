package net.blueshell.api.platform.integration.contact

import net.blueshell.api.domain.user.application.contact.ContactData
import net.blueshell.api.domain.user.application.contact.ContactServiceException
import net.blueshell.api.domain.user.application.contact.ContactSyncAdapter
import net.blueshell.clients.listmonk.api.ListsApi
import net.blueshell.clients.listmonk.api.SubscribersApi
import net.blueshell.clients.listmonk.model.NewList as ListmonkNewList
import net.blueshell.clients.listmonk.model.NewListOptin
import net.blueshell.clients.listmonk.model.NewListType
import net.blueshell.clients.listmonk.model.NewSubscriber
import net.blueshell.clients.listmonk.model.SubscriberQueryRequest
import net.blueshell.clients.listmonk.model.SubscriberQueryRequestAction
import net.blueshell.clients.listmonk.model.UpdateSubscriber
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Primary
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClientResponseException

/**
 * Listmonk Contact Anti-Corruption Layer (ADR-019)
 *
 * Implements [ContactSyncAdapter] against the Listmonk subscriber API.
 * Active in all non-test profiles (including dev where real Listmonk runs).
 *
 * Listmonk is used for both transactional email and contact/subscriber management.
 * Subscriber IDs are integers in Listmonk; they are returned as strings to satisfy
 * the [ContactSyncAdapter] contract, and stored as `Long` on the User entity.
 */
@Service
@Primary
@Profile("!test")
class ListmonkContactAdapter(
    private val subscribersApi: SubscribersApi,
    private val listsApi: ListsApi,
) : ContactSyncAdapter {

    override fun syncContact(userId: Long, contactData: ContactData): String {
        log.info("Syncing contact for user {}: {}", userId, contactData.email)

        return try {
            val existing = findSubscriberByEmail(contactData.email)
            if (existing != null) {
                val update = buildUpdateSubscriber(contactData)
                subscribersApi.updateSubscriberById(existing, update)
                log.info("Updated Listmonk subscriber id={} for user {}", existing, userId)
                existing.toString()
            } else {
                val newSub = buildNewSubscriber(contactData)
                val created = subscribersApi.createSubscriber(newSub)?.data
                    ?: throw ContactServiceException("Listmonk returned null when creating subscriber for user $userId")
                val id = created.id ?: throw ContactServiceException("Listmonk subscriber has no id for user $userId")
                log.info("Created Listmonk subscriber id={} for user {}", id, userId)
                id.toString()
            }
        } catch (e: RestClientResponseException) {
            log.error("Failed to sync contact for user {} to Listmonk", userId, e)
            throw ContactServiceException("Failed to sync contact", e)
        }
    }

    override fun getContactId(userId: Long, email: String): String? {
        log.debug("Getting Listmonk contact id for user {}: {}", userId, email)
        return try {
            findSubscriberByEmail(email)?.toString()
        } catch (e: RestClientResponseException) {
            log.error("Failed to get contact id for user {}", userId, e)
            throw ContactServiceException("Failed to get contact id", e)
        }
    }

    override fun addToList(listId: String, contactId: String) {
        log.info("Adding Listmonk contact {} to list {}", contactId, listId)
        try {
            val req = SubscriberQueryRequest()
                .ids(listOf(contactId.toInt()))
                .action(SubscriberQueryRequestAction.ADD)
            subscribersApi.manageSubscriberListById(listId.toInt(), req)
        } catch (e: RestClientResponseException) {
            log.error("Failed to add contact {} to list {}", contactId, listId, e)
            throw ContactServiceException("Failed to add contact to list", e)
        } catch (e: NumberFormatException) {
            throw ContactServiceException("Invalid id format: listId=$listId, contactId=$contactId", e)
        }
    }

    override fun removeFromList(listId: String, contactId: String) {
        log.info("Removing Listmonk contact {} from list {}", contactId, listId)
        try {
            val req = SubscriberQueryRequest()
                .ids(listOf(contactId.toInt()))
                .action(SubscriberQueryRequestAction.REMOVE)
            subscribersApi.manageSubscriberListById(listId.toInt(), req)
        } catch (e: RestClientResponseException) {
            log.error("Failed to remove contact {} from list {}", contactId, listId, e)
            throw ContactServiceException("Failed to remove contact from list", e)
        } catch (e: NumberFormatException) {
            throw ContactServiceException("Invalid id format: listId=$listId, contactId=$contactId", e)
        }
    }

    override fun deleteContact(contactId: String) {
        log.info("Deleting Listmonk subscriber {}", contactId)
        try {
            subscribersApi.deleteSubscriberById(contactId.toInt())
        } catch (e: RestClientResponseException) {
            log.error("Failed to delete Listmonk subscriber {}", contactId, e)
            throw ContactServiceException("Failed to delete contact", e)
        } catch (e: NumberFormatException) {
            throw ContactServiceException("Invalid contactId format: $contactId", e)
        }
    }

    override fun createList(listName: String, folderName: String): String {
        log.info("Creating Listmonk list '{}'", listName)
        return try {
            val newList = ListmonkNewList()
                .name(listName)
                .type(NewListType.PRIVATE)
                .optin(NewListOptin.SINGLE)
            val created = listsApi.createList(newList)?.data
                ?: throw ContactServiceException("Listmonk returned null when creating list '$listName'")
            val id = created.id ?: throw ContactServiceException("Listmonk list has no id for '$listName'")
            log.info("Created Listmonk list '{}' id={}", listName, id)
            id.toString()
        } catch (e: RestClientResponseException) {
            log.error("Failed to create Listmonk list '{}'", listName, e)
            throw ContactServiceException("Failed to create list", e)
        }
    }

    // ---- helpers ----

    private fun findSubscriberByEmail(email: String): Int? {
        val escapedEmail = email.replace("'", "''")
        val response = subscribersApi.getSubscribers(
            1,                              // page
            10,                             // perPage
            "subscribers.email = '$escapedEmail'",  // query
            null, null, null, null,
        )
        return response?.data?.results?.firstOrNull()?.id
    }

    private fun buildNewSubscriber(contactData: ContactData): NewSubscriber =
        NewSubscriber()
            .email(contactData.email)
            .name("${contactData.firstName} ${contactData.lastName}".trim())
            .status("enabled")
            .preconfirmSubscriptions(true)
            .attribs(buildAttribs(contactData))

    private fun buildUpdateSubscriber(contactData: ContactData): UpdateSubscriber =
        UpdateSubscriber()
            .email(contactData.email)
            .name("${contactData.firstName} ${contactData.lastName}".trim())
            .status("enabled")
            .attribs(buildAttribs(contactData))

    private fun buildAttribs(contactData: ContactData): Map<String, Any> =
        buildMap {
            put("newsletter", contactData.newsletter)
            put("is_member", contactData.isMember)
            contactData.phoneNumber?.let { put("phone", it) }
            putAll(contactData.attributes)
        }

    companion object {
        private val log = LoggerFactory.getLogger(ListmonkContactAdapter::class.java)
    }
}
