package net.blueshell.api.mail.domain

import net.blueshell.api.email.api.SentEmails
import net.blueshell.api.mail.persistence.InboxMessage
import net.blueshell.api.mail.persistence.InboxMessageRepository
import net.blueshell.api.mail.persistence.InboxState
import net.blueshell.api.user.api.UserService
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

/** The site's email a received message answers. */
data class AnsweredEmail(
    val emailId: Long,
    val emailType: String,
    val sentAt: Instant?,
)

/** One received message as the Inbox lists it. */
data class InboxEntry(
    val id: Long,
    val fromAddress: String,
    val fromName: String?,
    val toAddress: String?,
    val subject: String,
    val receivedAt: Instant,
    val state: InboxState,
    val automatic: Boolean,
    val senderUserId: Long?,
    val senderName: String?,
    val handledBy: Long?,
    val handledByName: String?,
    val handledAt: Instant?,
    val answers: AnsweredEmail?,
)

/** What the Inbox counts: new, dealt with, and automatic replies kept apart. */
data class InboxCounts(
    val new: Long,
    val oldestNewAt: Instant?,
    val done: Long,
    val automatic: Long,
)

@Service
class Inbox(
    private val messages: InboxMessageRepository,
    private val sent: SentEmails,
    private val users: UserService,
) {
    @Transactional(readOnly = true)
    fun page(
        search: String?,
        pageable: Pageable,
    ): Page<InboxEntry> {
        val found = messages.search(search?.trim()?.ifEmpty { null }, pageable)
        val answered = sent.byIds(found.content.mapNotNull { it.answersEmailId })
        val names =
            users
                .findAllByIds(found.content.flatMap { listOfNotNull(it.senderUserId, it.handledBy) }.toSet())
                .associate { requireNotNull(it.id) to it.fullName }
        return PageImpl(found.content.map { entryOf(it, answered, names) }, pageable, found.totalElements)
    }

    @Transactional(readOnly = true)
    fun counts(): InboxCounts =
        InboxCounts(
            new = messages.countByStateAndAutomaticFalse(InboxState.NEW),
            oldestNewAt = messages.findFirstByStateAndAutomaticFalseOrderByReceivedAtAsc(InboxState.NEW)?.receivedAt,
            done = messages.countByStateAndAutomaticFalse(InboxState.REPLIED) + messages.countByStateAndAutomaticFalse(InboxState.HANDLED),
            automatic = messages.countByAutomaticTrue(),
        )

    private fun entryOf(
        message: InboxMessage,
        answered: Map<Long, net.blueshell.api.email.api.SentEmailRef>,
        names: Map<Long, String>,
    ) = InboxEntry(
        id = requireNotNull(message.id),
        fromAddress = message.fromAddress,
        fromName = message.fromName,
        toAddress = message.toAddress,
        subject = message.subject,
        receivedAt = message.receivedAt,
        state = message.state,
        automatic = message.automatic,
        senderUserId = message.senderUserId,
        senderName = message.senderUserId?.let(names::get),
        handledBy = message.handledBy,
        handledByName = message.handledBy?.let(names::get),
        handledAt = message.handledAt,
        answers = message.answersEmailId?.let(answered::get)?.let { AnsweredEmail(it.id, it.emailType, it.sentAt) },
    )
}
