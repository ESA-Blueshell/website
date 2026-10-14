package net.blueshell.api.mail.domain

import io.swagger.v3.oas.annotations.media.Schema
import net.blueshell.api.email.api.SentEmailRef
import net.blueshell.api.email.api.SentEmails
import net.blueshell.api.mail.persistence.InboxMessage
import net.blueshell.api.mail.persistence.InboxMessageRepository
import net.blueshell.api.mail.persistence.InboxReply
import net.blueshell.api.mail.persistence.InboxReplyRepository
import net.blueshell.api.mail.persistence.InboxState
import net.blueshell.api.user.api.UserService
import org.jsoup.Jsoup
import org.jsoup.nodes.TextNode
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

/** What a conversation holds: what the site sent, what came back, and the board's replies. */
@Schema(name = "ConversationKind", enumAsRef = true)
enum class ConversationKind {
    SENT,
    RECEIVED,
    REPLY,
}

/** One step of a conversation, oldest first; a sent email links to Sent rather than repeat its body. */
data class ConversationItem(
    val kind: ConversationKind,
    val at: Instant?,
    val subject: String?,
    /** The text of what came back, or the board's reply as written; null for a sent email. */
    val body: String?,
    val fromAddress: String?,
    val emailId: Long?,
    val inboxMessageId: Long?,
    val writtenByName: String?,
)

/** Mail with the same person outside this conversation, newest first. */
data class EarlierMail(
    val kind: ConversationKind,
    val at: Instant?,
    val subject: String,
    val emailId: Long?,
    val inboxMessageId: Long?,
)

data class Conversation(
    val message: InboxEntry,
    val items: List<ConversationItem>,
    val earlier: List<EarlierMail>,
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
    private val replies: InboxReplyRepository,
    private val sent: SentEmails,
    private val users: UserService,
) {
    /**
     * A received message with the conversation it belongs to: the site's email it answers, every
     * message the same person sent in answer to it, and the board's replies, oldest first.
     */
    @Transactional(readOnly = true)
    fun conversation(id: Long): Conversation {
        val received = messages.findById(id).orElseThrow { InboxMessageNotFound() }
        val fromThem = messages.findTop20ByFromAddressOrderByReceivedAtDesc(received.fromAddress)
        val inThread = threadOf(received, fromThem)
        val written = replies.findByInboxMessageIdInOrderByWrittenAtAsc(inThread.mapNotNull { it.id })
        val answered = sent.byIds(inThread.mapNotNull { it.answersEmailId }.distinct())
        val names =
            users
                .findAllByIds((written.mapNotNull { it.writtenBy } + listOfNotNull(received.senderUserId, received.handledBy)).toSet())
                .associate { requireNotNull(it.id) to it.fullName }
        return Conversation(
            message = entryOf(received, answered, names),
            // The board's own replies show as replies, not again as what the site sent.
            items = itemsOf(answered.values.filter { it.emailType != MailJobs.InboxReply.type }, inThread, written, names),
            earlier = earlierWith(received, answered.keys, fromThem.filter { mail -> inThread.none { it.id == mail.id } }),
        )
    }

    private fun itemsOf(
        answered: Collection<SentEmailRef>,
        inThread: List<InboxMessage>,
        written: List<InboxReply>,
        names: Map<Long, String>,
    ): List<ConversationItem> {
        val sentItems = answered.map { ConversationItem(ConversationKind.SENT, it.sentAt, it.subject, null, null, it.id, null, null) }
        val receivedItems =
            inThread.map {
                val body = it.bodyText ?: it.bodyHtml?.let(::plainOf)
                ConversationItem(ConversationKind.RECEIVED, it.receivedAt, it.subject, body, it.fromAddress, null, it.id, null)
            }
        val replyItems =
            written.map {
                val by = it.writtenBy?.let(names::get)
                ConversationItem(ConversationKind.REPLY, it.writtenAt, null, it.message, null, null, it.inboxMessageId, by)
            }
        return (sentItems + receivedItems + replyItems).sortedBy { it.at }
    }

    /**
     * Every message from the same address that shares an id with the conversation, followed until
     * nothing more joins: an answer to the board's reply names the earlier messages in References.
     */
    private fun threadOf(
        received: InboxMessage,
        fromThem: List<InboxMessage>,
    ): List<InboxMessage> {
        val thread = mutableListOf(received)
        val ids = idsOf(received).toMutableSet()
        do {
            val joining = fromThem.filter { mail -> thread.none { it.id == mail.id } && idsOf(mail).any(ids::contains) }
            for (mail in joining) {
                thread += mail
                ids += idsOf(mail)
            }
        } while (joining.isNotEmpty())
        return thread
    }

    // Its own id, the ids it names, and the site's email it answers.
    private fun idsOf(mail: InboxMessage): Set<String> {
        val named = SPACES.split(mail.threadIds.orEmpty().trim()).filter(String::isNotEmpty)
        return setOf(mail.messageId) + named + listOfNotNull(mail.answersEmailId?.let { "email:$it" })
    }

    /** What the site sent to the address and what else came from it, outside the conversation. */
    private fun earlierWith(
        received: InboxMessage,
        inConversation: Set<Long>,
        otherMessages: List<InboxMessage>,
    ): List<EarlierMail> {
        val sentToThem =
            sent
                .toAddress(received.fromAddress)
                .filter { it.id !in inConversation }
                .map { EarlierMail(ConversationKind.SENT, it.sentAt, it.subject, it.id, null) }
        val fromThem = otherMessages.map { EarlierMail(ConversationKind.RECEIVED, it.receivedAt, it.subject, null, it.id) }
        return (sentToThem + fromThem).sortedByDescending { it.at }
    }

    @Transactional(readOnly = true)
    fun page(
        search: String?,
        pageable: Pageable,
    ): Page<InboxEntry> {
        val typed = search?.trim()?.ifEmpty { null }
        val found = messages.search(typed?.let { literal(it.take(SEARCH_LIMIT)) }, pageable)
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
        answered: Map<Long, SentEmailRef>,
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

private const val SEARCH_LIMIT = 100

private val SPACES = Regex(" +")

// What was typed, matched as written: `%` and `_` are no wildcards. `!` is the query's escape.
private fun literal(search: String) = search.replace("!", "!!").replace("%", "!%").replace("_", "!_")

/** The text of an HTML-only message, a line per paragraph, so the page never renders mail it received. */
internal fun plainOf(html: String): String {
    val document = Jsoup.parse(html)
    document.select("br").forEach { it.replaceWith(TextNode("\n")) }
    document.select("p, div, li, tr, h1, h2, h3").forEach { it.appendChild(TextNode("\n")) }
    return document
        .wholeText()
        .lines()
        .joinToString("\n") { it.trim() }
        .replace(Regex("\n{3,}"), "\n\n")
        .trim()
}
