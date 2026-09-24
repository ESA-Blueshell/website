package net.blueshell.api.shared.job

import java.time.Instant

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

    /**
     * Tells somebody a board member took their sign-up off an event. The sign-up is gone by the
     * time this runs, so the payload carries everything the email needs rather than an id.
     */
    object EventSignUpRemoved : JobDefinition<EventSignUpRemovedPayload> {
        override val type: String = "email.event-signup-removed"
        override val payloadType: Class<EventSignUpRemovedPayload> = EventSignUpRemovedPayload::class.java

        override fun dedupKey(payload: EventSignUpRemovedPayload): String? = null
    }

    object ContributionReminder : JobDefinition<ContributionReminderPayload> {
        override val type: String = "email.contribution-reminder"
        override val payloadType: Class<ContributionReminderPayload> = ContributionReminderPayload::class.java

        override fun dedupKey(payload: ContributionReminderPayload): String? = null
    }

    /**
     * The ask made on joining. A separate type from [ContributionReminder] rather than a
     * column on the record: the two render different emails from the same row.
     */
    object JoiningContribution : JobDefinition<JoiningContributionPayload> {
        override val type: String = "email.joining-contribution"
        override val payloadType: Class<JoiningContributionPayload> = JoiningContributionPayload::class.java

        override fun dedupKey(payload: JoiningContributionPayload): String? = null
    }

    object IncassoNotification : JobDefinition<IncassoNotificationPayload> {
        override val type: String = "email.incasso-notification"
        override val payloadType: Class<IncassoNotificationPayload> = IncassoNotificationPayload::class.java

        override fun dedupKey(payload: IncassoNotificationPayload): String? = null
    }

    /**
     * Telling somebody that what they may reach has changed. Only a change to admin or board
     * queues one; the rest change quietly.
     */
    object RoleChange : JobDefinition<RoleChangePayload> {
        override val type: String = "email.role-change"
        override val payloadType: Class<RoleChangePayload> = RoleChangePayload::class.java

        override fun dedupKey(payload: RoleChangePayload): String? = null
    }

    data class RecoveryPayload(
        val userId: Long,
        val token: String,
        val tokenPurpose: TokenPurpose,
    )

    data class EventSignupPayload(
        val eventSignUpId: Long,
        val guestAccessToken: String,
    )

    data class EventSignUpRemovedPayload(
        val recipientEmail: String,
        val recipientName: String,
        val eventTitle: String,
    )

    /** The ask's own id: a member can be asked for the same period twice, so the pair is not a key. */
    data class ContributionReminderPayload(
        val contributionReminderId: Long,
    )

    /** The ask's own id, as with a reminder. */
    data class JoiningContributionPayload(
        val contributionReminderId: Long,
    )

    data class IncassoNotificationPayload(
        val incassoNotificationId: Long,
    )

    /** The record's own id: the email states the change that was written, not the roles held now. */
    data class RoleChangePayload(
        val roleChangeId: Long,
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

object DiscordPostJobs {
    /** Brings one event's posts and Discord event in the server to what should stand. */
    object Reconcile : JobDefinition<ReconcilePayload> {
        override val type: String = "discord.reconcile-event-posts"
        override val payloadType: Class<ReconcilePayload> = ReconcilePayload::class.java
    }

    /**
     * [trigger] is `MORNING` or `CHANGE`; [at] is the moment to judge as, which the morning run
     * fixes at 08:00 so a retry later that day judges the same, and a change leaves null for now.
     */
    data class ReconcilePayload(
        val eventId: Long,
        val trigger: String,
        val at: Instant? = null,
    )
}

object CalendarJobs {
    object SyncCalendarEvent : JobDefinition<SyncCalendarEventPayload> {
        override val type: String = "calendar.sync-event"
        override val payloadType: Class<SyncCalendarEventPayload> = SyncCalendarEventPayload::class.java
    }

    data class SyncCalendarEventPayload(
        val eventId: Long,
    )
}

/**
 * A picture's own widths, derived away from whatever asked for them.
 *
 * Only an animation is queued. A still costs one converter run per width and is written where
 * it was asked for; an animation costs one per frame per width, which is not something a start
 * or a request should wait on.
 */
object ImageJobs {
    object DeriveRenditions : JobDefinition<DeriveRenditionsPayload> {
        override val type: String = "image.derive-renditions"
        override val payloadType: Class<DeriveRenditionsPayload> = DeriveRenditionsPayload::class.java
    }

    /** The picture's own id: its widths are derived from whatever the record points at now. */
    data class DeriveRenditionsPayload(
        val fileId: Long,
    )
}
