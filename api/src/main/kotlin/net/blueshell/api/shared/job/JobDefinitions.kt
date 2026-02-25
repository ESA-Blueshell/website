package net.blueshell.api.shared.job

import net.blueshell.api.shared.enums.ResetType

object EmailJobs {
    object Recovery : JobDefinition<RecoveryPayload> {
        override val type: String = "email.recovery"
        override val payloadType: Class<RecoveryPayload> = RecoveryPayload::class.java
    }

    object EventSignup : JobDefinition<EventSignupPayload> {
        override val type: String = "email.event-signup"
        override val payloadType: Class<EventSignupPayload> = EventSignupPayload::class.java
    }

    object ContributionReminder : JobDefinition<ContributionReminderPayload> {
        override val type: String = "email.contribution-reminder"
        override val payloadType: Class<ContributionReminderPayload> = ContributionReminderPayload::class.java
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
        override fun dedupKey(payload: CalendarEventRef): String = "event=${payload.eventId}"
    }
}

object ContactJobs {
    object SyncContact : JobDefinition<SyncContactPayload> {
        override val type: String = "contact.sync"
        override val payloadType: Class<SyncContactPayload> = SyncContactPayload::class.java
        override fun dedupKey(payload: SyncContactPayload): String = "user=${payload.userId}"
    }

    object DeleteContact : JobDefinition<DeleteContactPayload> {
        override val type: String = "contact.delete"
        override val payloadType: Class<DeleteContactPayload> = DeleteContactPayload::class.java
        override fun dedupKey(payload: DeleteContactPayload): String = "user=${payload.userId}"
    }

    object SyncListMembership : JobDefinition<SyncListMembershipPayload> {
        override val type: String = "contact.sync-list-membership"
        override val payloadType: Class<SyncListMembershipPayload> = SyncListMembershipPayload::class.java
        override fun dedupKey(payload: SyncListMembershipPayload): String =
            "user=${payload.userId}:period=${payload.periodId}"
    }

    data class SyncContactPayload(
        val userId: Long
    )

    data class DeleteContactPayload(
        val userId: Long,
        val contactId: Long
    )

    data class SyncListMembershipPayload(
        val userId: Long,
        val periodId: Long
    )
}

data class CalendarEventRef(val eventId: Long)
