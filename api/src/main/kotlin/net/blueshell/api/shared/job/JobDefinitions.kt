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
    object SpawnContactSyncs : JobDefinition<SpawnContactSyncsPayload> {
        override val type: String = "contact.spawn-syncs"
        override val payloadType: Class<SpawnContactSyncsPayload> = SpawnContactSyncsPayload::class.java
        // No dedup: always run, each invocation may cover a different set of users
        override fun dedupKey(payload: SpawnContactSyncsPayload): String? = null
    }

    object SpawnListMembershipSyncs : JobDefinition<SpawnListMembershipSyncsPayload> {
        override val type: String = "contact.spawn-list-syncs"
        override val payloadType: Class<SpawnListMembershipSyncsPayload> = SpawnListMembershipSyncsPayload::class.java
        override fun dedupKey(payload: SpawnListMembershipSyncsPayload): String? = null
    }

    object DeleteContact : JobDefinition<DeleteContactPayload> {
        override val type: String = "contact.delete"
        override val payloadType: Class<DeleteContactPayload> = DeleteContactPayload::class.java
    }

    object SyncListMembership : JobDefinition<SyncListMembershipPayload> {
        override val type: String = "contact.sync-list-membership"
        override val payloadType: Class<SyncListMembershipPayload> = SyncListMembershipPayload::class.java
    }

    object SyncContactForSystem : CommandJobDefinition<SyncContactCommand> {
        override val type: String = "contact.sync-for-system"
        override val payloadType: Class<SyncContactCommand> = SyncContactCommand::class.java
    }

    object SyncListMembershipForSystem : CommandJobDefinition<SyncListMembershipCommand> {
        override val type: String = "contact.list-sync-for-system"
        override val payloadType: Class<SyncListMembershipCommand> = SyncListMembershipCommand::class.java
    }

    data class SpawnContactSyncsPayload(val unused: Unit = Unit)
    data class SpawnListMembershipSyncsPayload(val unused: Unit = Unit)

    data class DeleteContactPayload(
        val userId: Long
    )

    data class SyncListMembershipPayload(
        val userId: Long,
        val periodId: Long
    )
}

data class CalendarEventRef(val eventId: Long)
