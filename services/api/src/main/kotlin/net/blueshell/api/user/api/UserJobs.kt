package net.blueshell.api.user.api

import net.blueshell.api.shared.job.JobDefinition

object UserJobs {
    /**
     * Telling somebody that what they may reach has changed. Only a change to admin or board
     * queues one; the rest change quietly.
     */
    object RoleChange : JobDefinition<RoleChangePayload> {
        override val type: String = "email.role-change"
        override val payloadType: Class<RoleChangePayload> = RoleChangePayload::class.java

        override fun dedupKey(payload: RoleChangePayload): String? = null
    }

    /** The record's own id: the email states the change that was written, not the roles held now. */
    data class RoleChangePayload(
        val roleChangeId: Long,
    )
}
