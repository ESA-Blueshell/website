package net.blueshell.api.platform.integration.contact.adapter.listmonk

import net.blueshell.api.platform.integration.contact.adapter.ContactData
import net.blueshell.api.platform.integration.contact.adapter.ContactServiceException
import net.blueshell.api.platform.integration.contact.adapter.ContactListAdapter
import net.blueshell.api.shared.enums.ContactSystem
import net.blueshell.clients.listmonk.api.ListsApi
import net.blueshell.clients.listmonk.api.SubscribersApi
import net.blueshell.clients.listmonk.model.*
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClientResponseException
import net.blueshell.clients.listmonk.model.NewList as ListmonkNewList

/**
 * Listmonk Anti-Corruption Layer (ADR-019)
 *
 * Implements [ContactSystemAdapter] against the Listmonk subscriber and lists APIs.
 * Active in all non-test profiles (including dev where real Listmonk runs).
 */
@Service
@Profile("!test")
class ListmonkListAdapter(
    private val subscribersApi: SubscribersApi,
    private val listsApi: ListsApi,
) : ContactListAdapter {

    override val system = ContactSystem.LISTMONK

    override fun createList(name: String, folderName: String?): Long {
        log.info("Creating Listmonk list '{}'", name)
        return try {
            val newList = ListmonkNewList()
                .name(name)
                .type(NewListType.PRIVATE)
                .optin(NewListOptin.SINGLE)
            val created = listsApi.createList(newList)?.data
                ?: throw ContactServiceException("Listmonk returned null when creating list '$name'")
            val id = created.id ?: throw ContactServiceException("Listmonk list has no id for '$name'")
            log.info("Created Listmonk list '{}' id={}", name, id)
            id.toLong()
        } catch (e: RestClientResponseException) {
            log.error("Failed to create Listmonk list '{}'", name, e)
            throw ContactServiceException("Failed to create list", e)
        }
    }

    override fun addToList(externalContactId: Long, externalListId: Long) {
        log.info("Adding Listmonk subscriber {} to list {}", externalContactId, externalListId)
        try {
            val req = SubscriberQueryRequest()
                .ids(listOf(externalContactId.toInt()))
                .action(SubscriberQueryRequestAction.ADD)
            subscribersApi.manageSubscriberListById(externalListId.toInt(), req)
        } catch (e: RestClientResponseException) {
            log.error("Failed to add subscriber {} to list {}", externalContactId, externalListId, e)
            throw ContactServiceException("Failed to add contact to list", e)
        }
    }

    override fun removeFromList(externalContactId: Long, externalListId: Long) {
        log.info("Removing Listmonk subscriber {} from list {}", externalContactId, externalListId)
        try {
            val req = SubscriberQueryRequest()
                .ids(listOf(externalContactId.toInt()))
                .action(SubscriberQueryRequestAction.REMOVE)
            subscribersApi.manageSubscriberListById(externalListId.toInt(), req)
        } catch (e: RestClientResponseException) {
            log.error("Failed to remove subscriber {} from list {}", externalContactId, externalListId, e)
            throw ContactServiceException("Failed to remove contact from list", e)
        }
    }

    override fun deleteList(externalListId: Long) {
        log.info("Deleting Listmonk list id={}", externalListId)
        try {
            listsApi.deleteListById(externalListId.toInt())
        } catch (e: RestClientResponseException) {
            log.error("Failed to delete Listmonk list id={}", externalListId, e)
            throw ContactServiceException("Failed to delete list", e)
        }
    }

    // ── private builders ──────────────────────────────────────────────────────

    private fun buildNewSubscriber(data: ContactData): NewSubscriber =
        NewSubscriber()
            .email(data.email)
            .name("${data.firstName} ${data.lastName}".trim())
            .status("enabled")
            .preconfirmSubscriptions(true)
            .attribs(buildAttribs(data))

    private fun buildUpdateSubscriber(data: ContactData): UpdateSubscriber =
        UpdateSubscriber()
            .email(data.email)
            .name("${data.firstName} ${data.lastName}".trim())
            .status("enabled")
            .attribs(buildAttribs(data))

    private fun buildAttribs(data: ContactData): Map<String, Any> =
        buildMap {
            put("newsletter", data.newsletter)
            put("is_member", data.isMember)
            data.phoneNumber?.let { put("phone", it) }
            putAll(data.attributes)
        }

    companion object {
        private val log = LoggerFactory.getLogger(ListmonkListAdapter::class.java)
    }
}
