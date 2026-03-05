package net.blueshell.api.platform.integration.contact.adapter

import net.blueshell.api.domain.user.application.contact.ContactServiceException
import net.blueshell.api.domain.user.application.contact.ContactSystem
import net.blueshell.api.domain.user.application.contact.ListSyncAdapter
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
 * Brevo List Anti-Corruption Layer (ADR-019)
 *
 * Implements [ListSyncAdapter] against the Brevo Contacts API.
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
    @param:Value($$"${brevo.apiKey}") apiKey: String,
    @param:Value($$"${brevo.baseUrl:https://api.brevo.com/v3}") brevoBaseUrl: String,
    @param:Value($$"${brevo.folders.contributionPeriodsId}") private val contributionPeriodsFolder: Long,
) : ListSyncAdapter {

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

    override fun addToList(systemContactId: Long, systemListId: Long) {
        log.info("Adding Brevo contact {} to list {}", systemContactId, systemListId)
        try {
            val req = AddContactToListRequest()
            req.ids = mutableListOf(systemContactId)
            req.emails = null
            req.extIds = null
            contactsApi.addContactToList(systemListId, req)
        } catch (e: RestClientResponseException) {
            log.error("Failed to add contact {} to Brevo list {}", systemContactId, systemListId, e)
            throw ContactServiceException("Failed to add contact to list", e)
        }
    }

    override fun removeFromList(systemContactId: Long, systemListId: Long) {
        log.info("Removing Brevo contact {} from list {}", systemContactId, systemListId)
        try {
            val req = RemoveContactFromListRequest()
            req.ids = mutableListOf(systemContactId)
            req.emails = null
            req.extIds = null
            contactsApi.removeContactFromList(systemListId, req)
        } catch (e: RestClientResponseException) {
            log.error("Failed to remove contact {} from Brevo list {}", systemContactId, systemListId, e)
            throw ContactServiceException("Failed to remove contact from list", e)
        }
    }

    override fun deleteList(systemListId: Long) {
        log.info("Deleting Brevo list id={}", systemListId)
        try {
            contactsApi.deleteList(systemListId)
        } catch (e: RestClientResponseException) {
            log.error("Failed to delete Brevo list id={}", systemListId, e)
            throw ContactServiceException("Failed to delete list", e)
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(BrevoListAdapter::class.java)
    }
}
