package net.blueshell.api.platform.integration.contact

import net.blueshell.api.shared.enums.Role
import net.blueshell.api.feature.user.model.User
import net.blueshell.api.feature.contribution.model.ContributionPeriod
import net.blueshell.api.feature.user.service.UserService
import net.blueshell.clients.brevo.api.ContactsApi
import net.blueshell.clients.brevo.model.*
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientResponseException

@Service
class ContactService(
    private val users: UserService,
    private val restClientBuilder: RestClient.Builder,
    @param:Value($$"${brevo.apiKey}") private val apiKey: String,
    @param:Value($$"${brevo.baseUrl:https://api.brevo.com/v3}") private val brevoBaseUrl: String,
    @param:Value($$"${brevo.folders.contributionPeriodsId}") private val contributionPeriodsFolder: Long,
) {
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
        log.info("Getting update for user: {}", user.email)

        try {
            val api = this.contactsApi
            val details =
                api.getContactInfo(user.email, "email_id", null, null)
            user.contactId = details.id
        } catch (_: HttpClientErrorException) {
            log.info("Failed to get contact details for user: {}", user.email)
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
        log.info("Creating contact for user: {}", user.email)
        val api = this.contactsApi
        val createContact = toCreateContact(user)
        val response = api.createContact(createContact)
        users.updateContactId(user.id!!, response.id!!)
    }

    @Throws(RestClientResponseException::class)
    private fun sendUpdate(user: User) {
        log.info("Sending update for user: {}", user.email)
        val api = this.contactsApi
        val contact = toUpdateContact(user)
        api.updateContact(
            user.email,
            contact,
            "email_id"
        )
    }

    private fun toCreateContact(user: User): CreateContact {
        val contact = CreateContact()
        contact.email = user.email
        contact.extId = user.id.toString()
        contact.attributes = toAttributes(user)
        return contact
    }

    private fun toUpdateContact(user: User): UpdateContact {
        val contact = UpdateContact()
        contact.extId = user.id.toString()
        contact.attributes = toAttributes(user)
        return contact
    }

    private fun toAttributes(user: User): MutableMap<String, Any> {
        val attrs: MutableMap<String, Any> = HashMap()
        attrs["NEWSLETTER"] = user.newsletter
        attrs["IS_MEMBER"] = user.hasRole(Role.MEMBER)
        attrs["FIRSTNAME"] = user.firstName
        attrs["LASTNAME"] = user.lastName
        attrs["SURNAME"] = user.lastName
        attrs["SMS"] = user.phoneNumber!!
        attrs["WHATSAPP"] = user.phoneNumber!!
        return attrs
    }

    @Throws(RestClientResponseException::class)
    fun createList(contributionPeriod: ContributionPeriod): Long {
        if (contributionPeriod.listId != null) {
            return contributionPeriod.listId!!
        }

        val api = this.contactsApi

        val periodName = String.format(
            "Contribution Paid %d - %d",
            contributionPeriod.startDate.year,
            contributionPeriod.endDate.year
        )
        val createList = CreateList(
            periodName,
            contributionPeriodsFolder
        )
        val createModel = api.createList(createList)
        return createModel.id
    }

    @Throws(RestClientResponseException::class)
    fun addToList(contributionPeriod: ContributionPeriod, user: User) {
        if (user.contactId == null) {
            sync(user)
        }

        val api = this.contactsApi
        val ids: MutableList<Long> = ArrayList()
        ids.add(user.contactId!!)
        val payload = AddContactToListRequest()
        payload.ids = ids
        api.addContactToList(contributionPeriod.listId!!, payload)
    }

    @Throws(RestClientResponseException::class)
    fun removeFromList(contributionPeriod: ContributionPeriod, contactId: Long) {
        val api = this.contactsApi
        val ids: MutableList<Long> = ArrayList()
        ids.add(contactId)
        val payload = RemoveContactFromListRequest()
        payload.ids = ids
        api.removeContactFromList(contributionPeriod.listId!!, payload)
    }

    companion object {
        private val log = LoggerFactory.getLogger(ContactService::class.java)
    }
}
