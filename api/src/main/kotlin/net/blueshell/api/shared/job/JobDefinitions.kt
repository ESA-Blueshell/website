package net.blueshell.api.shared.job

import net.blueshell.api.shared.enums.ContactSystem
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
    object SyncContact : JobDefinition<SyncContactPayload> {
        override val type: String = "contact.sync"
        override val payloadType: Class<SyncContactPayload> = SyncContactPayload::class.java
    }

    object DeleteContact : JobDefinition<DeleteContactPayload> {
        override val type: String = "contact.delete"
        override val payloadType: Class<DeleteContactPayload> = DeleteContactPayload::class.java
    }

    object SyncListMembership : JobDefinition<SyncListMembershipPayload> {
        override val type: String = "contact.sync-list-membership"
        override val payloadType: Class<SyncListMembershipPayload> = SyncListMembershipPayload::class.java
    }

    object SyncContactToSystem : JobDefinition<SyncContactToSystemPayload> {
        override val type: String = "contact.sync-to-system"
        override val payloadType: Class<SyncContactToSystemPayload> = SyncContactToSystemPayload::class.java
    }

    object DeleteContactFromSystem : JobDefinition<DeleteContactFromSystemPayload> {
        override val type: String = "contact.delete-from-system"
        override val payloadType: Class<DeleteContactFromSystemPayload> = DeleteContactFromSystemPayload::class.java
    }

    object AddToList : JobDefinition<AddToListPayload> {
        override val type: String = "contact.add-to-list"
        override val payloadType: Class<AddToListPayload> = AddToListPayload::class.java
    }

    object RemoveFromList : JobDefinition<RemoveFromListPayload> {
        override val type: String = "contact.remove-from-list"
        override val payloadType: Class<RemoveFromListPayload> = RemoveFromListPayload::class.java
    }

    data class SyncContactPayload(
        val userId: Long
    )

    data class DeleteContactPayload(
        val userId: Long
    )

    data class SyncListMembershipPayload(
        val userId: Long,
        val periodId: Long
    )

    data class SyncContactToSystemPayload(
        val userId: Long,
        val system: ContactSystem
    )

    data class DeleteContactFromSystemPayload(
        val externalId: Long,
        val system: ContactSystem
    )

    data class AddToListPayload(
        val userId: Long,
        val contactListId: Long,
        val system: ContactSystem
    )

    data class RemoveFromListPayload(
        val userId: Long,
        val contactListId: Long,
        val system: ContactSystem
    )
}

data class CalendarEventRef(val eventId: Long)
