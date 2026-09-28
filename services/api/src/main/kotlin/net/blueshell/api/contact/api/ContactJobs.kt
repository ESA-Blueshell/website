package net.blueshell.api.contact.api

import net.blueshell.api.shared.job.JobDefinition

object ContactJobs {
    object SyncAllContacts : JobDefinition<SyncAllContactsPayload> {
        override val type: String = "contact.sync-all"
        override val payloadType: Class<SyncAllContactsPayload> = SyncAllContactsPayload::class.java

        // No dedup: always run, each invocation may cover a different set of users
        override fun dedupKey(payload: SyncAllContactsPayload): String? = null
    }

    object SyncContact : JobDefinition<SyncContactPayload> {
        override val type: String = "contact.sync"
        override val payloadType: Class<SyncContactPayload> = SyncContactPayload::class.java
    }

    object RemoveContact : JobDefinition<RemoveContactPayload> {
        override val type: String = "contact.remove"
        override val payloadType: Class<RemoveContactPayload> = RemoveContactPayload::class.java
    }

    data class SyncAllContactsPayload(
        val unused: Unit = Unit,
    )

    data class SyncContactPayload(
        val userId: Long,
    )

    data class RemoveContactPayload(
        val userId: Long,
    )
}
