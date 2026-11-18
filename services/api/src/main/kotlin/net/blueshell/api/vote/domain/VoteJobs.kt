package net.blueshell.api.vote.domain

import net.blueshell.api.shared.job.JobDefinition

object VoteJobs {
    /** Reveals every annual vote whose reveal date has passed. Safe to run again: a revealed vote is skipped. */
    object RevealDue : JobDefinition<RevealDuePayload> {
        override val type: String = "vote.reveal-due"
        override val payloadType: Class<RevealDuePayload> = RevealDuePayload::class.java
    }

    /** Nothing to say: the job reaches every vote that is due, and one payload dedupes with the next. */
    data class RevealDuePayload(
        val unused: Unit = Unit,
    )
}
