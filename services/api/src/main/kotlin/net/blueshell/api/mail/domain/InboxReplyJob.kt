package net.blueshell.api.mail.domain

import net.blueshell.api.email.api.EmailJob
import net.blueshell.api.email.api.EmailSenderService
import net.blueshell.api.email.api.SiteMarkdownEmails
import net.blueshell.api.mail.persistence.InboxMessageRepository
import net.blueshell.api.mail.persistence.InboxReplyRepository
import net.blueshell.api.shared.email.EmailContent
import net.blueshell.api.shared.job.requireExists
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.server.ResponseStatusException
import tools.jackson.databind.ObjectMapper

/** Sends a reply to the address the message came from, threaded with the conversation it answers. */
@Component
class InboxReplyJob(
    objectMapper: ObjectMapper,
    emails: EmailSenderService,
    private val replies: InboxReplyRepository,
    private val messages: InboxMessageRepository,
    private val siteMarkdown: SiteMarkdownEmails,
) : EmailJob<MailJobs.InboxReplyPayload>(objectMapper, MailJobs.InboxReply, emails) {
    override fun compose(payload: MailJobs.InboxReplyPayload): EmailContent {
        val reply = requireExists { replies.findById(payload.inboxReplyId).orElseThrow { gone() } }
        val received = requireExists { messages.findById(reply.inboxMessageId).orElseThrow { gone() } }
        val subject = received.subject.trim().ifEmpty { "Your message" }
        // Stored newest first, the way a reply's thread is searched; References runs oldest first.
        val thread =
            received.threadIds
                ?.split(" ")
                ?.filter { it.isNotBlank() }
                ?.reversed()
                .orEmpty()
        return EmailContent(
            recipientEmail = received.fromAddress,
            recipientName = received.fromName ?: received.fromAddress,
            subject = (if (subject.startsWith("Re:", ignoreCase = true)) subject else "Re: $subject").take(SUBJECT_LIMIT),
            markdownContent = siteMarkdown.forEmail(reply.message),
            replyToOverride = reply.replyTo,
            inReplyTo = received.messageId,
            references = newestThatFit(thread + received.messageId),
        )
    }

    // A header line holds 998 characters; the newest ids matter most to a mail client's threading.
    private fun newestThatFit(ids: List<String>): List<String> {
        val kept = ArrayDeque<String>()
        var length = -1
        for (id in ids.asReversed()) {
            if (length + 1 + id.length > REFERENCES_LIMIT) break
            kept.addFirst(id)
            length += 1 + id.length
        }
        return kept.toList()
    }

    private fun gone() = ResponseStatusException(HttpStatus.NOT_FOUND, "The reply or its message is gone")
}

/** The longest References header a reply carries. */
internal const val REFERENCES_LIMIT = 900
