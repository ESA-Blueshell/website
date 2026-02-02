package net.blueshell.api.service

import com.fasterxml.jackson.annotation.JsonInclude
import lombok.extern.slf4j.Slf4j
import net.blueshell.api.mapper.BrevoContactMapper
import net.blueshell.api.model.User
import net.blueshell.api.model.contribution.ContributionPeriod
import net.blueshell.clients.brevo.api.ContactsApi
import net.blueshell.clients.brevo.invoker.ApiClient
import net.blueshell.clients.brevo.model.AddContactToListRequest
import net.blueshell.clients.brevo.model.CreateList
import net.blueshell.clients.brevo.model.RemoveContactFromListRequest
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.RestClientResponseException

@Slf4j
@Service
class ContactService(private val mapper: BrevoContactMapper, private val users: UserService) {
    @Value("\${brevo.apiKey}")
    private val apiKey: String? = null

    @Value("\${brevo.folders.contributionPeriodsId}")
    private val contributionPeriodsFolder: Long? = null

    private val contacts: ContactsApi? = null

    private val contactsApi: ContactsApi
        get() {
            val dateFormat = ApiClient.createDefaultDateFormat()
            val objectMapper =
                ApiClient.createDefaultObjectMapper(dateFormat)
                    .setSerializationInclusion(JsonInclude.Include.NON_EMPTY)
                    .setDefaultPropertyInclusion(
                        JsonInclude.Value.construct(
                            JsonInclude.Include.NON_EMPTY,
                            JsonInclude.Include.NON_EMPTY
                        )
                    )

            val apiClient = ApiClient(objectMapper, dateFormat)
            apiClient.setApiKey(this.apiKey)
            return ContactsApi(apiClient)
        }

    fun getUpdate(user: User) {
        if (user.getContactId() != null) return
        ContactService.log.info("Getting update for user: {}", user.getEmail())

        try {
            val api = this.contactsApi
            val details =
                api.getContactInfo(user.getEmail(), "email_id", null, null)
            user.setContactId(details.id)
        } catch (e: HttpClientErrorException) {
            ContactService.log.info("Failed to get contact details for user: {}", user.getEmail())
        }
    }

    fun sync(user: User) {
        getUpdate(user)
        if (user.getContactId() != null) {
            sendUpdate(user)
        } else {
            createContact(user)
        }
    }

    @Throws(RestClientResponseException::class)
    private fun createContact(user: User) {
        ContactService.log.info("Creating contact for user: {}", user.getEmail())
        val api = this.contactsApi
        val createContact = mapper.toCreate(user)
        val response = api.createContact(createContact)
        users.updateContactId(user.getId(), response.id)
    }

    @Throws(RestClientResponseException::class)
    private fun sendUpdate(user: User) {
        ContactService.log.info("Sending update for user: {}", user.getEmail())
        val api = this.contactsApi
        val contact = mapper.toUpdate(user)
        api.updateContact(
            user.getEmail(),
            contact,
            "email_id"
        )
    }

    @Throws(RestClientResponseException::class)
    fun createList(contributionPeriod: ContributionPeriod): Long? {
        if (contributionPeriod.getListId() != null) {
            return contributionPeriod.getListId()
        }

        val api = this.contactsApi
        val createList = CreateList()
        val periodName = String.format(
            "Contribution Paid %d - %d",
            contributionPeriod.getStartDate().getYear(),
            contributionPeriod.getEndDate().getYear()
        )
        createList.name(periodName)
        createList.folderId = contributionPeriodsFolder
        val createModel = api.createList(createList)
        return createModel.id
    }

    @Throws(RestClientResponseException::class)
    fun addToList(contributionPeriod: ContributionPeriod, user: User) {
        if (user.getContactId() == null) {
            sync(user)
        }

        val api = this.contactsApi
        val ids: MutableList<Long?> = ArrayList<Long?>()
        ids.add(user.getContactId())
        val payload = AddContactToListRequest()
        payload.ids = ids
        api.addContactToList(contributionPeriod.getListId(), payload)
    }

    @Throws(RestClientResponseException::class)
    fun removeFromList(contributionPeriod: ContributionPeriod, user: User) {
        val api = this.contactsApi
        val ids: MutableList<Long?> = ArrayList<Long?>()
        ids.add(user.getContactId())
        val payload = RemoveContactFromListRequest()
        payload.ids = ids
        api.removeContactFromList(contributionPeriod.getListId(), payload)
    }
}
