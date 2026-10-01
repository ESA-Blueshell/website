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

/** Who an email would reach, and how many it names have no address to reach. */
data class Reach(
    val userIds: Set<Long>,
    val withoutEmail: Int,
)

/** What the board writes and to whom: one email kept, one copy queued per person, each in Sent. */
@Service
class Writing(
    private val audiences: CohortAudiences,
    private val users: UserService,
    private val written: WrittenEmailRepository,
    private val jobs: JobQueue,
    private val clock: Clock,
) {
    /** Everybody the addressees name once, left out where they have no address. */
    @Transactional(readOnly = true)
    fun reach(to: Collection<Addressee>): Reach {
        val named =
            to.flatMapTo(mutableSetOf()) { addressee ->
                when (addressee.kind) {
                    AddresseeKind.COHORT -> audiences.membersOf(addressee.id)
                    AddresseeKind.ROLE ->
                        Role.entries
                            .firstOrNull { it.name == addressee.id }
                            ?.let(users::findIdsHolding)
                            .orEmpty()
                    AddresseeKind.PERSON -> setOfNotNull(addressee.id.toLongOrNull())
                }
            }
        val reachable =
            users
                .findAllByIds(named)
                .filter { it.email.isNotBlank() }
                .mapNotNull { it.id }
                .toSet()
        return Reach(reachable, named.size - reachable.size)
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
    ): Int = queue(setOf(writer), subject, message, replyTo, writer)

    private fun queue(
        people: Set<Long>,
        subject: String,
        message: String,
        replyTo: String?,
        writer: Long?,
    ): Int {
        if (subject.isBlank()) throw SubjectMissing()
        if (message.isBlank()) throw MessageMissing()
        if (people.isEmpty()) throw NobodyToWrite()
        val email = written.save(WrittenEmail(subject.trim(), message, replyTo?.ifBlank { null }, writer, clock.instant(), people.size))
        people.sorted().forEach { userId ->
            jobs.runAsync(MailJobs.Written, MailJobs.WrittenPayload(requireNotNull(email.id), userId), JobTrigger.SITE_ACTION)
        }
        return people.size
    }
}
