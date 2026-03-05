package net.blueshell.api.platform.integration.contact.adapter

import net.blueshell.api.domain.user.application.contact.ContactData
import net.blueshell.api.domain.user.application.contact.ContactServiceException
import net.blueshell.api.domain.user.application.contact.ContactSyncAdapter
import net.blueshell.api.domain.user.application.contact.ContactSystem
import net.blueshell.clients.listmonk.api.SubscribersApi
import net.blueshell.clients.listmonk.model.NewSubscriber
import net.blueshell.clients.listmonk.model.UpdateSubscriber
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClientResponseException

/**
 * Listmonk Contact Anti-Corruption Layer (ADR-019)
 *
 * Implements [ContactSyncAdapter] against the Listmonk subscriber API.
 * Active in all non-test profiles (including dev where real Listmonk runs).
 *
 * All IDs are system-specific Longs. The orchestration services resolve domain IDs to these
 * system IDs before invoking adapter methods.
 */
@Service
@Profile("!test")
class ListmonkContactAdapter(
    private val subscribersApi: SubscribersApi,
) : ContactSyncAdapter {

    override val system = ContactSystem.LISTMONK

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

    override fun updateContact(externalId: Long, data: ContactData) {
        log.info("Updating Listmonk subscriber id={}: {}", externalId, data.email)
        try {
            val update = buildUpdateSubscriber(data)
            subscribersApi.updateSubscriberById(externalId.toInt(), update)
            log.info("Updated Listmonk subscriber id={}", externalId)
        } catch (e: RestClientResponseException) {
            log.error("Failed to update Listmonk subscriber id={}", externalId, e)
            throw ContactServiceException("Failed to update contact", e)
        }
    }

    override fun deleteContact(externalId: Long) {
        log.info("Deleting Listmonk subscriber id={}", externalId)
        try {
            subscribersApi.deleteSubscriberById(externalId.toInt())
        } catch (e: RestClientResponseException) {
            log.error("Failed to delete Listmonk subscriber id={}", externalId, e)
            throw ContactServiceException("Failed to delete contact", e)
        }
    }

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
