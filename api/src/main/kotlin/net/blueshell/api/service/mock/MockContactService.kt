package net.blueshell.api.service.mock

import net.blueshell.api.mapper.BrevoContactMapper
import net.blueshell.api.model.User
import net.blueshell.api.model.contribution.ContributionPeriod
import net.blueshell.api.service.ContactService
import net.blueshell.api.service.UserService
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Primary
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
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
    mapper: BrevoContactMapper,
    private val users: UserService
) : ContactService(mapper, users) {
    private val contactSeq = AtomicLong(100000L)
    private val listSeq = AtomicLong(10000L)

    private val emailToContactId: ConcurrentMap<String, Long> = ConcurrentHashMap<String, Long>()
    private val listMembers: ConcurrentMap<Long, MutableSet<Long>> = ConcurrentHashMap<Long, MutableSet<Long>>()

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
            users.updateContactId(user.id, id)
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
        listMembers.putIfAbsent(listId, ConcurrentHashMap.newKeySet<Long>())
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
        listMembers.computeIfAbsent(listId) { _: Long -> ConcurrentHashMap.newKeySet() }!!.add(user.contactId)
        log.info("[brevo-mock] added contactId={} to listId={}", user.contactId, listId)
    }

    @Throws(RestClientResponseException::class)
    override fun removeFromList(contributionPeriod: ContributionPeriod, user: User) {
        val listId = contributionPeriod.listId
        if (listId == null) return
        listMembers.computeIfPresent(listId) { id: Long, set: MutableSet<Long> ->
            set!!.remove(user.contactId)
            set
        }
        log.info("[brevo-mock] removed contactId={} from listId={}", user.contactId, listId)
    }

    fun getEmailToContactId(): MutableMap<String, Long> {
        return Collections.unmodifiableMap<String, Long>(emailToContactId)
    }

    fun getListMembers(): MutableMap<Long, MutableSet<Long>> {
        return Collections.unmodifiableMap<Long, MutableSet<Long>>(listMembers)
    }

    companion object {
        private val log = LoggerFactory.getLogger(MockContactService::class.java)
    }
}
