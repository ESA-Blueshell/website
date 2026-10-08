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

    /** Seals every address still in plaintext, soft-deleted ones included, and empties the plaintext. Safe to run again. */
    object SealAddresses : JobDefinition<SealAddressesPayload> {
        override val type: String = "user.seal-addresses"
        override val payloadType: Class<SealAddressesPayload> = SealAddressesPayload::class.java
    }

    /** Nothing to say: the job reaches every address left. */
    data class SealAddressesPayload(
        val reason: String? = null,
    )

    /** Moves every sealed value below the newest version of its key onto it. Safe to run again. */
    object RewrapSealedValues : JobDefinition<RewrapSealedValuesPayload> {
        override val type: String = "user.rewrap-sealed-values"
        override val payloadType: Class<RewrapSealedValuesPayload> = RewrapSealedValuesPayload::class.java
    }

    /** Nothing to say: the job reaches every sealed field. */
    data class RewrapSealedValuesPayload(
        val reason: String? = null,
    )
}
