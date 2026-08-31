package net.blueshell.api.shared.job

import net.blueshell.api.shared.enums.TokenPurpose


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

    object IncassoNotification : JobDefinition<IncassoNotificationPayload> {
        override val type: String = "email.incasso-notification"
        override val payloadType: Class<IncassoNotificationPayload> = IncassoNotificationPayload::class.java
        override fun dedupKey(payload: IncassoNotificationPayload): String? = null
    }

    data class RecoveryPayload(
        val userId: Long,
        val token: String,
        val tokenPurpose: TokenPurpose
    )

    data class EventSignupPayload(
        val eventSignUpId: Long,
        val guestAccessToken: String
    )

    /**
     * The ask's own id. A member can be asked for the same period more than once, so the
     * pair no longer names one row.
     */
    data class ContributionReminderPayload(
        val contributionReminderId: Long
    )

    data class IncassoNotificationPayload(
        val incassoNotificationId: Long
    )
}

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

    data class SyncAllContactsPayload(val unused: Unit = Unit)

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
