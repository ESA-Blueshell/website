package net.blueshell.api.mail.domain

import io.swagger.v3.oas.annotations.media.Schema
import net.blueshell.api.cohort.api.CohortAudiences
import net.blueshell.api.mail.persistence.WrittenEmail
import net.blueshell.api.mail.persistence.WrittenEmailRepository
import net.blueshell.api.shared.enums.Role
import net.blueshell.api.shared.job.JobQueue
import net.blueshell.api.shared.job.JobTrigger
import net.blueshell.api.user.api.UserService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock

/** Who an email is addressed to: a cohort by its key, a role, or a person. */
@Schema(name = "Addressee")
data class Addressee(
    val kind: AddresseeKind,
    val id: String,
)

@Schema(name = "AddresseeKind", enumAsRef = true)
enum class AddresseeKind {
    COHORT,
    ROLE,
    PERSON,
}

/** Who an email would reach, and how many it names are left out: no address, or never activated. */
data class Reach(
    val userIds: Set<Long>,
    val withoutEmail: Int,
    val notActivated: Int,
)

// Nobody to write to: the visitor without an account, and the site's own account.
private val NOT_AN_AUDIENCE = setOf(Role.ANONYMOUS, Role.SYSTEM)

/** What the board writes and to whom: one email kept, one copy queued per person, each in Sent. */
@Service
class Writing(
    private val audiences: CohortAudiences,
    private val users: UserService,
    private val written: WrittenEmailRepository,
    private val jobs: JobQueue,
    private val clock: Clock,
) {
    /** Everybody the addressees name once, left out where they have no address or never activated. */
    @Transactional(readOnly = true)
    fun reach(to: Collection<Addressee>): Reach {
        val named =
            to.flatMapTo(mutableSetOf()) { addressee ->
                when (addressee.kind) {
                    AddresseeKind.COHORT -> audiences.membersOf(addressee.id)
                    AddresseeKind.ROLE ->
                        Role.entries
                            .firstOrNull { it.name == addressee.id && it !in NOT_AN_AUDIENCE }
                            ?.let(users::findIdsHolding)
                            .orEmpty()
                    AddresseeKind.PERSON -> setOfNotNull(addressee.id.toLongOrNull())
                }
            }
        val people = users.findAllByIds(named).filter { Role.SYSTEM !in it.roles }
        val (activated, dormant) = people.partition { it.enabled }
        val (reachable, withoutEmail) = activated.partition { it.email.isNotBlank() }
        return Reach(reachable.mapNotNull { it.id }.toSet(), withoutEmail.size, dormant.size)
    }

    /** Keeps the email and queues one copy per person it reaches, attributed to whoever is writing. */
    @Transactional
    fun send(
        to: Collection<Addressee>,
        subject: String,
        message: String,
        replyTo: String?,
        writer: Long?,
    ): Int = queue(reach(to).userIds, subject, message, replyTo, writer)

    /** One copy to the writer, to see it as it will arrive. */
    @Transactional
    fun test(
        subject: String,
        message: String,
        replyTo: String?,
        writer: Long,
    ): Int {
        if (users.findAllByIds(setOf(writer)).none { it.email.isNotBlank() }) throw WriterWithoutAddress()
        return queue(setOf(writer), subject, message, replyTo, writer)
    }

    private fun queue(
        people: Set<Long>,
        subject: String,
        message: String,
        replyTo: String?,
        writer: Long?,
    ): Int {
        if (subject.isBlank()) throw SubjectMissing()
        if (subject.any { it == '\r' || it == '\n' }) throw SubjectNotOneLine()
        if (subject.trim().length > SUBJECT_LIMIT) throw SubjectTooLong()
        if (message.isBlank()) throw MessageMissing()
        if (message.length > MESSAGE_LIMIT) throw MessageTooLong()
        if (people.isEmpty()) throw NobodyToWrite()
        val email = written.save(WrittenEmail(subject.trim(), message, checkedReplyTo(replyTo), writer, clock.instant(), people.size))
        people.sorted().forEach { userId ->
            jobs.runAsync(MailJobs.Written, MailJobs.WrittenPayload(requireNotNull(email.id), userId), JobTrigger.SITE_ACTION)
        }
        return people.size
    }
}
