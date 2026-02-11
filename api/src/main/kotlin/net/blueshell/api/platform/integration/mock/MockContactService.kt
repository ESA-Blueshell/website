package net.blueshell.api.platform.integration.mock

import net.blueshell.api.domain.contribution.persistence.ContributionPeriod
import net.blueshell.api.platform.integration.contact.ContactService
import net.blueshell.api.domain.user.application.UserService
import net.blueshell.api.domain.user.persistence.User
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Primary
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientResponseException
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Test double for ContactService; models an in-memory contact store and list membership.
 */
@Service
@Primary
@Profile("test | dev")
class MockContactService(
    private val users: UserService,
    restClientBuilder: RestClient.Builder,
    @Value($$"${brevo.apiKey:}") apiKey: String,
    @Value($$"${brevo.baseUrl:https://api.brevo.com/v3}") brevoBaseUrl: String,
    @Value($$"${brevo.folders.contributionPeriodsId:0}") contributionPeriodsFolder: Long,
) : ContactService(users, restClientBuilder, apiKey, brevoBaseUrl, contributionPeriodsFolder) {
    private val contactSeq = AtomicLong(100000L)
    private val listSeq = AtomicLong(10000L)

    private val emailToContactId: ConcurrentMap<String, Long> = ConcurrentHashMap()
    private val listMembers: ConcurrentMap<Long, MutableSet<Long>> = ConcurrentHashMap()

    override fun getUpdate(user: User) {
        if (user.contactId != null) return
        val id = emailToContactId[user.email]
        if (id != null) {
            user.contactId = id
            log.debug("[brevo-mock] found existing contactId={} for email={}", id, user.email)
        } else {
            log.debug("[brevo-mock] no contact yet for email={}", user.email)
        }
    }

    override fun sync(user: User) {
        getUpdate(user)
        if (user.contactId == null) {
            val id =
                emailToContactId.computeIfAbsent(user.email) { k: String -> contactSeq.andIncrement }!!
            user.contactId = id
            users.updateContactId(user.id!!, id)
            log.info("[brevo-mock] created contact email={} -> id={}", user.email, id)
        } else {
            log.info(
                "[brevo-mock] updated contact email={} id={}",
                user.email,
                user.contactId
            )
        }
    }

    @Throws(RestClientResponseException::class)
    override fun createList(contributionPeriod: ContributionPeriod): Long {
        var listId = contributionPeriod.listId
        if (listId == null) {
            listId = listSeq.andIncrement
            log.info(
                "[brevo-mock] created listId={} for contributionPeriod id={}",
                listId,
                contributionPeriod.id
            )
        }
        listMembers.putIfAbsent(listId, ConcurrentHashMap.newKeySet())
        return listId
    }

    @Throws(RestClientResponseException::class)
    override fun addToList(contributionPeriod: ContributionPeriod, user: User) {
        if (user.contactId == null) {
            sync(user)
            users.update(user)
        }
        var listId = contributionPeriod.listId
        if (listId == null) {
            listId = createList(contributionPeriod)
        }
        val contactId = user.contactId ?: return
        listMembers.computeIfAbsent(listId) { _: Long -> ConcurrentHashMap.newKeySet() }!!.add(contactId)
        log.info("[brevo-mock] added contactId={} to listId={}", contactId, listId)
    }

    @Throws(RestClientResponseException::class)
    override fun removeFromList(contributionPeriod: ContributionPeriod, contactId: Long) {
        val listId = contributionPeriod.listId ?: return
        listMembers.computeIfPresent(listId) { id: Long, set: MutableSet<Long> ->
            set.remove(contactId)
            set
        }
        log.info("[brevo-mock] removed contactId={} from listId={}", contactId, listId)
    }

    fun getEmailToContactId(): MutableMap<String, Long> {
        return Collections.unmodifiableMap(emailToContactId)
    }

    fun getListMembers(): MutableMap<Long, MutableSet<Long>> {
        return Collections.unmodifiableMap(listMembers)
    }

    companion object {
        private val log = LoggerFactory.getLogger(MockContactService::class.java)
    }
}
