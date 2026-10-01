package net.blueshell.api.mail.domain

import net.blueshell.api.shared.job.JobDefinition

object MailJobs {
    /** One copy of a written email, to one person. */
    object Written : JobDefinition<WrittenPayload> {
        override val type: String = "email.written"
        override val payloadType: Class<WrittenPayload> = WrittenPayload::class.java

        override fun dedupKey(payload: WrittenPayload): String? = null
    }

    data class WrittenPayload(
        val writtenEmailId: Long,
        val userId: Long,
    )
}
