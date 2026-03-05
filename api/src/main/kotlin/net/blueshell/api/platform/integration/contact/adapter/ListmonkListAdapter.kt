package net.blueshell.api.platform.integration.contact.adapter

import net.blueshell.api.domain.user.application.contact.ContactServiceException
import net.blueshell.api.domain.user.application.contact.ContactSystem
import net.blueshell.api.domain.user.application.contact.ListSyncAdapter
import net.blueshell.clients.listmonk.api.ListsApi
import net.blueshell.clients.listmonk.api.SubscribersApi
import net.blueshell.clients.listmonk.model.NewList as ListmonkNewList
import net.blueshell.clients.listmonk.model.NewListOptin
import net.blueshell.clients.listmonk.model.NewListType
import net.blueshell.clients.listmonk.model.SubscriberQueryRequest
import net.blueshell.clients.listmonk.model.SubscriberQueryRequestAction
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClientResponseException

/**
 * Listmonk List Anti-Corruption Layer (ADR-019)
 *
 * Implements [ListSyncAdapter] against the Listmonk lists/subscribers API.
 * Active in all non-test profiles (including dev where real Listmonk runs).
 *
 * All IDs are system-specific Longs. The orchestration services resolve domain IDs to these
 * system IDs before invoking adapter methods.
 */
@Service
@Profile("!test")
class ListmonkListAdapter(
    private val subscribersApi: SubscribersApi,
    private val listsApi: ListsApi,
) : ListSyncAdapter {

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

    override fun addToList(externalId: Long, externalListId: Long) {
        log.info("Adding Listmonk subscriber {} to list {}", externalId, externalListId)
        try {
            val req = SubscriberQueryRequest()
                .ids(listOf(externalId.toInt()))
                .action(SubscriberQueryRequestAction.ADD)
            subscribersApi.manageSubscriberListById(externalListId.toInt(), req)
        } catch (e: RestClientResponseException) {
            log.error("Failed to add subscriber {} to list {}", externalId, externalListId, e)
            throw ContactServiceException("Failed to add contact to list", e)
        }
    }

    override fun removeFromList(externalId: Long, externalListId: Long) {
        log.info("Removing Listmonk subscriber {} from list {}", externalId, externalListId)
        try {
            val req = SubscriberQueryRequest()
                .ids(listOf(externalId.toInt()))
                .action(SubscriberQueryRequestAction.REMOVE)
            subscribersApi.manageSubscriberListById(externalListId.toInt(), req)
        } catch (e: RestClientResponseException) {
            log.error("Failed to remove subscriber {} from list {}", externalId, externalListId, e)
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

    companion object {
        private val log = LoggerFactory.getLogger(ListmonkListAdapter::class.java)
    }
}
