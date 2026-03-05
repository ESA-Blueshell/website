package net.blueshell.api.platform.integration.contact

import net.blueshell.api.domain.user.application.contact.ContactData
import net.blueshell.api.domain.user.application.contact.ContactServiceException
import net.blueshell.api.domain.user.application.contact.ContactSyncAdapter
import net.blueshell.api.domain.user.application.contact.ContactSystem
import net.blueshell.api.domain.user.application.contact.ListSyncAdapter
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
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClientResponseException

/**
 * Listmonk Contact Anti-Corruption Layer (ADR-019)
 *
 * Implements [ContactSyncAdapter] and [ListSyncAdapter] against the Listmonk subscriber/lists API.
 * Active in all non-test profiles (including dev where real Listmonk runs).
 *
 * All IDs are system-specific Longs. The orchestration services resolve domain IDs to these
 * system IDs before invoking adapter methods.
 */
@Service
@Profile("!test")
class ListmonkContactAdapter(
    private val subscribersApi: SubscribersApi,
    private val listsApi: ListsApi,
) : ContactSyncAdapter, ListSyncAdapter {

    override val system = ContactSystem.LISTMONK

    // ── ContactSyncAdapter ────────────────────────────────────────────────────

    override fun createContact(data: ContactData): Long {
        log.info("Creating Listmonk subscriber: {}", data.email)
        return try {
            val newSub = buildNewSubscriber(data)
            val created = subscribersApi.createSubscriber(newSub)?.data
                ?: throw ContactServiceException("Listmonk returned null when creating subscriber for ${data.email}")
            val id = created.id ?: throw ContactServiceException("Listmonk subscriber has no id for ${data.email}")
            log.info("Created Listmonk subscriber id={} for {}", id, data.email)
            id.toLong()
        } catch (e: RestClientResponseException) {
            log.error("Failed to create Listmonk subscriber for {}", data.email, e)
            throw ContactServiceException("Failed to create contact", e)
        }
    }

    override fun updateContact(systemContactId: Long, data: ContactData) {
        log.info("Updating Listmonk subscriber id={}: {}", systemContactId, data.email)
        try {
            val update = buildUpdateSubscriber(data)
            subscribersApi.updateSubscriberById(systemContactId.toInt(), update)
            log.info("Updated Listmonk subscriber id={}", systemContactId)
        } catch (e: RestClientResponseException) {
            log.error("Failed to update Listmonk subscriber id={}", systemContactId, e)
            throw ContactServiceException("Failed to update contact", e)
        }
    }

    override fun deleteContact(systemContactId: Long) {
        log.info("Deleting Listmonk subscriber id={}", systemContactId)
        try {
            subscribersApi.deleteSubscriberById(systemContactId.toInt())
        } catch (e: RestClientResponseException) {
            log.error("Failed to delete Listmonk subscriber id={}", systemContactId, e)
            throw ContactServiceException("Failed to delete contact", e)
        }
    }

    // ── ListSyncAdapter ───────────────────────────────────────────────────────

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

    override fun addToList(systemContactId: Long, systemListId: Long) {
        log.info("Adding Listmonk subscriber {} to list {}", systemContactId, systemListId)
        try {
            val req = SubscriberQueryRequest()
                .ids(listOf(systemContactId.toInt()))
                .action(SubscriberQueryRequestAction.ADD)
            subscribersApi.manageSubscriberListById(systemListId.toInt(), req)
        } catch (e: RestClientResponseException) {
            log.error("Failed to add subscriber {} to list {}", systemContactId, systemListId, e)
            throw ContactServiceException("Failed to add contact to list", e)
        }
    }

    override fun removeFromList(systemContactId: Long, systemListId: Long) {
        log.info("Removing Listmonk subscriber {} from list {}", systemContactId, systemListId)
        try {
            val req = SubscriberQueryRequest()
                .ids(listOf(systemContactId.toInt()))
                .action(SubscriberQueryRequestAction.REMOVE)
            subscribersApi.manageSubscriberListById(systemListId.toInt(), req)
        } catch (e: RestClientResponseException) {
            log.error("Failed to remove subscriber {} from list {}", systemContactId, systemListId, e)
            throw ContactServiceException("Failed to remove contact from list", e)
        }
    }

    override fun deleteList(systemListId: Long) {
        log.info("Deleting Listmonk list id={}", systemListId)
        try {
            listsApi.deleteListById(systemListId.toInt())
        } catch (e: RestClientResponseException) {
            log.error("Failed to delete Listmonk list id={}", systemListId, e)
            throw ContactServiceException("Failed to delete list", e)
        }
    }

    // ── helpers ───────────────────────────────────────────────────────────────

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
        private val log = LoggerFactory.getLogger(ListmonkContactAdapter::class.java)
    }
}
