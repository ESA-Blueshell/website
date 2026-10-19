package net.blueshell.api.declaration.domain

import net.blueshell.api.shared.job.JobDefinition

object DeclarationJobs {
    /** Tells a submitter the board sent their declaration back or rejected it, with the board's reason. */
    object DeclarationReturned : JobDefinition<DeclarationReturnedPayload> {
        override val type: String = "email.declaration-returned"
        override val payloadType: Class<DeclarationReturnedPayload> = DeclarationReturnedPayload::class.java

        override fun dedupKey(payload: DeclarationReturnedPayload): String? = null
    }

    enum class Outcome { SENT_BACK, REJECTED }

    /** The declaration as the board acted on it, so the email names what they saw. */
    data class DeclarationReturnedPayload(
        val declarationId: Long,
        val submitterId: Long,
        val outcome: Outcome,
        val reason: String,
        val title: String,
        val totalCents: Int,
    )
}
