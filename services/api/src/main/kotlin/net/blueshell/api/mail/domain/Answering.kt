package net.blueshell.api.mail.domain

import net.blueshell.api.mail.persistence.InboxMessage
import net.blueshell.api.mail.persistence.InboxMessageRepository
import net.blueshell.api.mail.persistence.InboxReply
import net.blueshell.api.mail.persistence.InboxReplyRepository
import net.blueshell.api.mail.persistence.InboxState
import net.blueshell.api.shared.job.JobQueue
import net.blueshell.api.shared.job.JobTrigger
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock

/** The board answering a received message from the site, or marking it handled without a reply. */
@Service
class Answering(
    private val messages: InboxMessageRepository,
    private val replies: InboxReplyRepository,
    private val jobs: JobQueue,
    private val clock: Clock,
) {
    /** Keeps the reply on the conversation and queues the email it becomes, which shows in Sent. */
    @Transactional
    fun reply(
        messageId: Long,
        message: String,
        replyTo: String?,
        by: Long?,
    ): InboxMessage {
        if (message.isBlank()) throw MessageMissing()
        if (message.length > MESSAGE_LIMIT) throw MessageTooLong()
        val checked = checkedReplyTo(replyTo)
        val received = find(messageId)
        val reply = replies.save(InboxReply(messageId, message, checked, by, clock.instant()))
        jobs.runAsync(MailJobs.InboxReply, MailJobs.InboxReplyPayload(requireNotNull(reply.id)), JobTrigger.SITE_ACTION)
        return mark(received, InboxState.REPLIED, by)
    }

    /** Marks a new message handled; one already replied to or handled keeps who did that and when. */
    @Transactional
    fun markHandled(
        messageId: Long,
        by: Long?,
    ): InboxMessage {
        val received = find(messageId)
        return if (received.state == InboxState.NEW) mark(received, InboxState.HANDLED, by) else received
    }

    private fun mark(
        received: InboxMessage,
        state: InboxState,
        by: Long?,
    ): InboxMessage {
        received.state = state
        received.handledBy = by
        received.handledAt = clock.instant()
        return messages.save(received)
    }

    private fun find(id: Long): InboxMessage = messages.findById(id).orElseThrow { InboxMessageNotFound() }
}
