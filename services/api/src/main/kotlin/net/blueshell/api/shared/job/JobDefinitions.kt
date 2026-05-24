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

object CalendarJobs {
    object SyncEvent : JobDefinition<CalendarEventRef> {
        override val type: String = "calendar.sync-event"
        override val payloadType: Class<CalendarEventRef> = CalendarEventRef::class.java
    }
}

object ContactJobs {
    object DispatchContactSyncs : JobDefinition<DispatchContactSyncsPayload> {
        override val type: String = "contact.dispatch-syncs"
        override val payloadType: Class<DispatchContactSyncsPayload> = DispatchContactSyncsPayload::class.java
        // No dedup: always run, each invocation may cover a different set of users
        override fun dedupKey(payload: DispatchContactSyncsPayload): String? = null
    }

    object DispatchListMembershipSyncs : JobDefinition<DispatchListMembershipSyncsPayload> {
        override val type: String = "contact.dispatch-list-syncs"
        override val payloadType: Class<DispatchListMembershipSyncsPayload> = DispatchListMembershipSyncsPayload::class.java
        override fun dedupKey(payload: DispatchListMembershipSyncsPayload): String? = null
    }

    object DeleteContact : JobDefinition<DeleteContactPayload> {
        override val type: String = "contact.delete"
        override val payloadType: Class<DeleteContactPayload> = DeleteContactPayload::class.java
    }

    object ProcessListMembership : JobDefinition<ProcessListMembershipPayload> {
        override val type: String = "contact.process-list-membership"
        override val payloadType: Class<ProcessListMembershipPayload> = ProcessListMembershipPayload::class.java
    }

    object SyncContactToSystem : CommandJobDefinition<SyncContactCommand> {
        override val type: String = "contact.sync-to-system"
        override val payloadType: Class<SyncContactCommand> = SyncContactCommand::class.java
        // Upstream changes arriving while a run is in flight must enqueue a
        // successor; the running attempt may have read pre-change state.
        override val coalesceAgainstQueuedOnly: Boolean = true
    }

    object SyncListMembershipToSystem : CommandJobDefinition<SyncListMembershipCommand> {
        override val type: String = "contact.sync-list-to-system"
        override val payloadType: Class<SyncListMembershipCommand> = SyncListMembershipCommand::class.java
        override val coalesceAgainstQueuedOnly: Boolean = true
    }

    data class DispatchContactSyncsPayload(val unused: Unit = Unit)
    data class DispatchListMembershipSyncsPayload(val unused: Unit = Unit)

    data class DeleteContactPayload(
        val userId: Long
    )

    data class ProcessListMembershipPayload(
        val userId: Long,
        val periodId: Long
    )
}

data class CalendarEventRef(val eventId: Long)
