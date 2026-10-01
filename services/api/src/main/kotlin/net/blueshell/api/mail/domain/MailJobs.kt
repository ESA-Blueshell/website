package net.blueshell.api.mail.domain

import net.blueshell.api.shared.job.JobDefinition

object MailJobs {
    /** One copy of a written email, to one person. */
    object Written : JobDefinition<WrittenPayload> {
        override val type: String = "email.written"
        override val payloadType: Class<WrittenPayload> = WrittenPayload::class.java

        override fun dedupKey(payload: WrittenPayload): String? = null
    }

    /** A reply the board wrote to a received message, threaded with its conversation. */
    object InboxReply : JobDefinition<InboxReplyPayload> {
        override val type: String = "email.inbox-reply"
        override val payloadType: Class<InboxReplyPayload> = InboxReplyPayload::class.java

        override fun dedupKey(payload: InboxReplyPayload): String? = null
    }

    data class InboxReplyPayload(
        val inboxReplyId: Long,
    )

    data class WrittenPayload(
        val writtenEmailId: Long,
        val userId: Long,
    )
}
