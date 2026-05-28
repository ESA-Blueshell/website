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

    object ProcessListMembership : JobDefinition<ProcessListMembershipPayload> {
        override val type: String = "contact.process-list-membership"
        override val payloadType: Class<ProcessListMembershipPayload> = ProcessListMembershipPayload::class.java
    }

    object SyncListMembershipToSystem : CommandJobDefinition<SyncListMembershipCommand> {
        override val type: String = "contact.sync-list-to-system"
        override val payloadType: Class<SyncListMembershipCommand> = SyncListMembershipCommand::class.java
    }

    object EnsureContributionPeriodLists : JobDefinition<EnsureContributionPeriodListsPayload> {
        override val type: String = "contact.ensure-period-lists"
        override val payloadType: Class<EnsureContributionPeriodListsPayload> =
            EnsureContributionPeriodListsPayload::class.java
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

    data class DispatchContactSyncsPayload(val unused: Unit = Unit)
    data class DispatchListMembershipSyncsPayload(val unused: Unit = Unit)
    data class EnsureContributionPeriodListsPayload(val unused: Unit = Unit)

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
