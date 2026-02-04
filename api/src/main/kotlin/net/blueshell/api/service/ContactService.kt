package net.blueshell.api.service

import net.blueshell.api.mapper.BrevoContactMapper
import net.blueshell.api.model.User
import net.blueshell.api.model.contribution.ContributionPeriod
import net.blueshell.clients.brevo.api.ContactsApi
import net.blueshell.clients.brevo.model.AddContactToListRequest
import net.blueshell.clients.brevo.model.CreateList
import net.blueshell.clients.brevo.model.RemoveContactFromListRequest
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientResponseException

@Service
class ContactService(
    private val mapper: BrevoContactMapper,
    private val users: UserService,
    private val restClientBuilder: RestClient.Builder,
) {
    @Value($$"${brevo.apiKey}")
    private lateinit var apiKey: String

    @Value($$"${brevo.baseUrl:https://api.brevo.com/v3}")
    private lateinit var brevoBaseUrl: String

    @Value($$"${brevo.folders.contributionPeriodsId}")
    private lateinit var contributionPeriodsFolder: Long

    private lateinit var contacts: ContactsApi

    private val contactsApi: ContactsApi
        get() {
            val client = restClientBuilder
                .baseUrl(brevoBaseUrl)
                .defaultHeader("api-key", apiKey)
                .build()
            return ContactsApi(client)
        }

    fun getUpdate(user: User) {
        if (user.contactId != null) return
        ContactService.log.info("Getting update for user: {}", user.email)

        try {
            val api = this.contactsApi
            val details =
                api.getContactInfo(user.email, "email_id", null, null)
            user.contactId = details.id
        } catch (e: HttpClientErrorException) {
            ContactService.log.info("Failed to get contact details for user: {}", user.email)
        }
    }

    fun sync(user: User) {
        getUpdate(user)
        if (user.contactId != null) {
            sendUpdate(user)
        } else {
            createContact(user)
        }
    }

    @Throws(RestClientResponseException::class)
    private fun createContact(user: User) {
        ContactService.log.info("Creating contact for user: {}", user.email)
        val api = this.contactsApi
        val createContact = mapper.toCreate(user)
        val response = api.createContact(createContact)
        users.updateContactId(user.id, response.id)
    }

    @Throws(RestClientResponseException::class)
    private fun sendUpdate(user: User) {
        ContactService.log.info("Sending update for user: {}", user.email)
        val api = this.contactsApi
        val contact = mapper.toUpdate(user)
        api.updateContact(
            user.email,
            contact,
            "email_id"
        )
    }

    @Throws(RestClientResponseException::class)
    fun createList(contributionPeriod: ContributionPeriod): Long {
        if (contributionPeriod.listId != null) {
            return contributionPeriod.listId
        }

        val api = this.contactsApi
        val createList = CreateList()
        val periodName = String.format(
            "Contribution Paid %d - %d",
            contributionPeriod.startDate.year,
            contributionPeriod.endDate.year
        )
        createList.name(periodName)
        createList.folderId = contributionPeriodsFolder
        val createModel = api.createList(createList)
        return createModel.id
    }

    @Throws(RestClientResponseException::class)
    fun addToList(contributionPeriod: ContributionPeriod, user: User) {
        if (user.contactId == null) {
            sync(user)
        }

        val api = this.contactsApi
        val ids: MutableList<Long> = ArrayList<Long>()
        ids.add(user.contactId)
        val payload = AddContactToListRequest()
        payload.ids = ids
        api.addContactToList(contributionPeriod.listId, payload)
    }

    @Throws(RestClientResponseException::class)
    fun removeFromList(contributionPeriod: ContributionPeriod, user: User) {
        val api = this.contactsApi
        val ids: MutableList<Long> = ArrayList<Long>()
        ids.add(user.contactId)
        val payload = RemoveContactFromListRequest()
        payload.ids = ids
        api.removeContactFromList(contributionPeriod.listId, payload)
    }

    companion object {
        private val log = LoggerFactory.getLogger(ContactService::class.java)
    }
}
