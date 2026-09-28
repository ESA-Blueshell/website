package net.blueshell.api.auth.domain

import net.blueshell.api.shared.enums.TokenPurpose
import net.blueshell.api.shared.job.JobDefinition

object AuthJobs {
    object Recovery : JobDefinition<RecoveryPayload> {
        override val type: String = "email.recovery"
        override val payloadType: Class<RecoveryPayload> = RecoveryPayload::class.java

        override fun dedupKey(payload: RecoveryPayload): String? = null
    }

    /**
     * Telling somebody something changed about how they sign in, with the lock link that goes
     * with it, or telling an admin an account was locked.
     */
    object SecurityNotification : JobDefinition<SecurityNotificationPayload> {
        override val type: String = "email.security-notification"
        override val payloadType: Class<SecurityNotificationPayload> = SecurityNotificationPayload::class.java

        override fun dedupKey(payload: SecurityNotificationPayload): String? = null
    }

    /** Who a security notice is for: the person, the address they are leaving, or an admin. */
    enum class SecurityNotificationAudience { PERSON, OLD_ADDRESS, ADMINISTRATOR }

    /** The event's own id, and the lock link issued for it; the email states what was recorded. */
    data class SecurityNotificationPayload(
        val securityEventId: Long,
        val audience: SecurityNotificationAudience,
        val lockToken: String? = null,
        val recipientEmail: String? = null,
        val recipientUserId: Long? = null,
    )

    data class RecoveryPayload(
        val userId: Long,
        val token: String,
        val tokenPurpose: TokenPurpose,
    )
}
