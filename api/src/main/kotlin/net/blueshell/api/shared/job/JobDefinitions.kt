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
    object AddEvent : JobDefinition<CalendarEventRef> {
        override val type: String = "calendar.add-event"
        override val payloadType: Class<CalendarEventRef> = CalendarEventRef::class.java
    }

    object SyncEvent : JobDefinition<CalendarEventRef> {
        override val type: String = "calendar.sync-event"
        override val payloadType: Class<CalendarEventRef> = CalendarEventRef::class.java
    }

    object RemoveEvent : JobDefinition<CalendarEventRef> {
        override val type: String = "calendar.remove-event"
        override val payloadType: Class<CalendarEventRef> = CalendarEventRef::class.java
    }
}

object ContactJobs {
    object AddToList : JobDefinition<AddToListPayload> {
        override val type: String = "contact.add-to-list"
        override val payloadType: Class<AddToListPayload> = AddToListPayload::class.java
    }

    object RemoveFromList : JobDefinition<RemoveFromListPayload> {
        override val type: String = "contact.remove-from-list"
        override val payloadType: Class<RemoveFromListPayload> = RemoveFromListPayload::class.java
    }

    object SyncContact : JobDefinition<SyncContactPayload> {
        override val type: String = "contact.sync"
        override val payloadType: Class<SyncContactPayload> = SyncContactPayload::class.java
    }

    object CreateContributionPeriodList : JobDefinition<CreateContributionPeriodListPayload> {
        override val type: String = "contact.create-period-list"
        override val payloadType: Class<CreateContributionPeriodListPayload> =
            CreateContributionPeriodListPayload::class.java
    }

    data class AddToListPayload(
        val userId: Long,
        val periodId: Long
    )

    data class RemoveFromListPayload(
        val userId: Long,
        val periodId: Long
    )

    data class SyncContactPayload(
        val userId: Long
    )

    data class CreateContributionPeriodListPayload(
        val periodId: Long
    )
}

data class CalendarEventRef(val eventId: Long)
