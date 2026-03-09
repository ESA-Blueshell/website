package net.blueshell.api.platform.integration.contact.adapter.brevo

import net.blueshell.api.platform.integration.contact.adapter.ContactData
import net.blueshell.api.platform.integration.contact.adapter.ContactServiceException
import net.blueshell.api.platform.integration.contact.adapter.ListAdapter
import net.blueshell.api.shared.enums.ContactSystem
import net.blueshell.clients.brevo.ApiClient
import net.blueshell.clients.brevo.api.ContactsApi
import net.blueshell.clients.brevo.model.AddContactToListRequest
import net.blueshell.clients.brevo.model.CreateListRequest
import net.blueshell.clients.brevo.model.RemoveContactFromListRequest
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Profile
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientResponseException
import tools.jackson.databind.json.JsonMapper

/**
 * Brevo Anti-Corruption Layer (ADR-019)
 *
 * Implements [ContactSystemAdapter] against the Brevo Contacts API.
 * Active in production only (test/dev use MockContactAdapter).
 *
 * [contributionPeriodsFolder] is the Brevo folder ID under which all contribution-period
 * lists are created; the domain-level [folderName] hint is intentionally ignored because
 * Brevo organises lists by numeric folder ID, not by name.
 */
@Service
@Profile("!test & !dev")
class BrevoListAdapter(
    restClientBuilder: RestClient.Builder,
    jsonMapper: JsonMapper,
    @param:Value($$"${brevo.folders.contributionPeriodsId}") private val contributionPeriodsFolder: Long,
) : ListAdapter {
    @field:Value($$"${brevo.apiKey}")
    lateinit var apiKey: String

    @field:Value($$"${brevo.baseUrl:https://api.brevo.com/v3}")
    lateinit var brevoBaseUrl: String


    override val system = ContactSystem.BREVO

    private val contactsApi: ContactsApi = ContactsApi(
        ApiClient(
            restClientBuilder
                .baseUrl(brevoBaseUrl)
                .defaultHeader("api-key", apiKey)
                .configureMessageConverters {
                    it.addCustomConverter(JacksonJsonHttpMessageConverter(jsonMapper))
                }
                .build()
        )
    )

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

    override fun addToList(externalContactId: Long, externalListId: Long) {
        log.info("Adding Brevo contact {} to list {}", externalContactId, externalListId)
        try {
            val req = AddContactToListRequest()
            req.ids = mutableListOf(externalContactId)
            req.emails = null
            req.extIds = null
            contactsApi.addContactToList(externalListId, req)
        } catch (e: RestClientResponseException) {
            log.error("Failed to add contact {} to Brevo list {}", externalContactId, externalListId, e)
            throw ContactServiceException("Failed to add contact to list", e)
        }
    }

    override fun removeFromList(externalContactId: Long, externalListId: Long) {
        log.info("Removing Brevo contact {} from list {}", externalContactId, externalListId)
        try {
            val req = RemoveContactFromListRequest()
            req.ids = mutableListOf(externalContactId)
            req.emails = null
            req.extIds = null
            contactsApi.removeContactFromList(externalListId, req)
        } catch (e: RestClientResponseException) {
            log.error("Failed to remove contact {} from Brevo list {}", externalContactId, externalListId, e)
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
        private val log = LoggerFactory.getLogger(BrevoListAdapter::class.java)
    }
}
