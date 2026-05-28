package net.blueshell.api.shared.job

import net.blueshell.api.shared.enums.ResetType


object EmailJobs {
    object Recovery : JobDefinition<RecoveryPayload> {
        override val type: String = "email.recovery"
        override val payloadType: Class<RecoveryPayload> = RecoveryPayload::class.java
        override fun dedupKey(payload: RecoveryPayload): String? = null
    }

    object EventSignup : JobDefinition<EventSignupPayload> {
        override val type: String = "email.event-signup"
        override val payloadType: Class<EventSignupPayload> = EventSignupPayload::class.java
        override fun dedupKey(payload: EventSignupPayload): String? = null
    }

    object ContributionReminder : JobDefinition<ContributionReminderPayload> {
        override val type: String = "email.contribution-reminder"
        override val payloadType: Class<ContributionReminderPayload> = ContributionReminderPayload::class.java
        override fun dedupKey(payload: ContributionReminderPayload): String? = null
    }

    data class RecoveryPayload(
        val userId: Long,
        val token: String,
        val resetType: ResetType
    )

    data class EventSignupPayload(
        val eventSignUpId: Long,
        val guestAccessToken: String
    )

    data class ContributionReminderPayload(
        val userId: Long,
        val contributionPeriodId: Long
    )
}

object ContactJobs {
    object SyncAllContacts : JobDefinition<SyncAllContactsPayload> {
        override val type: String = "contact.sync-all"
        override val payloadType: Class<SyncAllContactsPayload> = SyncAllContactsPayload::class.java
        // No dedup: always run, each invocation may cover a different set of users
        override fun dedupKey(payload: SyncAllContactsPayload): String? = null
    }

    object SyncAllListMemberships : JobDefinition<SyncAllListMembershipsPayload> {
        override val type: String = "contact.list-sync-all"
        override val payloadType: Class<SyncAllListMembershipsPayload> = SyncAllListMembershipsPayload::class.java
        override fun dedupKey(payload: SyncAllListMembershipsPayload): String? = null
    }

    object ProcessListMembership : JobDefinition<ProcessListMembershipPayload> {
        override val type: String = "contact.process-list-membership"
        override val payloadType: Class<ProcessListMembershipPayload> = ProcessListMembershipPayload::class.java
    }

    object SyncListMembership : CommandJobDefinition<SyncListMembershipCommand> {
        override val type: String = "contact.list-sync"
        override val payloadType: Class<SyncListMembershipCommand> = SyncListMembershipCommand::class.java
    }

    object SyncAllPeriodLists : JobDefinition<SyncAllPeriodListsPayload> {
        override val type: String = "contact.period-list-sync-all"
        override val payloadType: Class<SyncAllPeriodListsPayload> =
            SyncAllPeriodListsPayload::class.java
        // Default payload-hash dedup is fine here: it only suppresses a second
        // concurrent active job (QUEUED/RUNNING) with the same empty payload,
        // not tomorrow's run. Manual triggers can still force a run via the
        // admin enqueue endpoint, which passes dedupKey = null.
    }

    object SyncContact : JobDefinition<SyncContactPayload> {
        override val type: String = "contact.sync"
        override val payloadType: Class<SyncContactPayload> = SyncContactPayload::class.java
    }

    object RemoveContact : JobDefinition<RemoveContactPayload> {
        override val type: String = "contact.remove"
        override val payloadType: Class<RemoveContactPayload> = RemoveContactPayload::class.java
    }

    data class SyncAllContactsPayload(val unused: Unit = Unit)
    data class SyncAllListMembershipsPayload(val unused: Unit = Unit)
    data class SyncAllPeriodListsPayload(val unused: Unit = Unit)

    data class ProcessListMembershipPayload(
        val userId: Long,
        val periodId: Long
    )

    data class SyncContactPayload(val userId: Long)
    data class RemoveContactPayload(val userId: Long)
}

object CalendarJobs {
    object SyncCalendarEvent : JobDefinition<SyncCalendarEventPayload> {
        override val type: String = "calendar.sync-event"
        override val payloadType: Class<SyncCalendarEventPayload> = SyncCalendarEventPayload::class.java
    }

    data class SyncCalendarEventPayload(val eventId: Long)
}
