package net.blueshell.api.contribution.domain

import net.blueshell.api.shared.job.JobDefinition

object ContributionJobs {
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
}
