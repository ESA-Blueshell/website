package net.blueshell.api.mail.domain

import net.blueshell.api.email.api.SentEmails
import net.blueshell.api.mail.persistence.InboxMessage
import net.blueshell.api.mail.persistence.InboxMessageRepository
import net.blueshell.api.user.api.UserService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/** Keeps a received message once, matched to the site's email it answers and to who sent it. */
@Service
class InboxIntake(
    private val messages: InboxMessageRepository,
    private val sent: SentEmails,
    private val users: UserService,
) {
    /** The kept message, read from [mailbox] (none for the catch-all), or null where it was kept before. */
    @Transactional
    fun take(
        parsed: ParsedInboxMessage,
        mailbox: String? = null,
    ): InboxMessage? {
        if (messages.existsByMessageId(parsed.messageId)) return null
        val answers = sent.answeredBy(parsed.threadIds)
        val sender = users.findAllByEmails(listOf(parsed.fromAddress)).firstOrNull()
        return messages.save(
            InboxMessage(
                messageId = parsed.messageId,
                inReplyTo = parsed.inReplyTo,
                threadIds = parsed.threadIds.joinToString(" ").ifEmpty { null },
                fromAddress = parsed.fromAddress,
                fromName = parsed.fromName,
                toAddress = parsed.toAddress,
                subject = parsed.subject,
                bodyText = parsed.bodyText,
                bodyHtml = parsed.bodyHtml,
                receivedAt = parsed.receivedAt,
                automatic = parsed.automatic,
                answersEmailId = answers?.id,
                senderUserId = sender?.id,
                mailbox = mailbox,
            ),
        )
    }
}
