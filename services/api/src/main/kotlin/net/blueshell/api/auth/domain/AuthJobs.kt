package net.blueshell.api.auth.domain

import net.blueshell.api.shared.enums.TokenPurpose
import net.blueshell.api.shared.job.JobDefinition

object AuthJobs {
    /** The email that carries a link of [purpose], one job type per email so the job list says which went out. */
    abstract class RecoveryEmail(
        val purpose: TokenPurpose,
    ) : JobDefinition<RecoveryPayload> {
        override val payloadType: Class<RecoveryPayload> = RecoveryPayload::class.java

        override fun dedupKey(payload: RecoveryPayload): String? = null
    }

    object AccountActivation : RecoveryEmail(TokenPurpose.USER_ACTIVATION) {
        override val type: String = "email.account-activation"
    }

    object MemberActivation : RecoveryEmail(TokenPurpose.MEMBER_ACTIVATION) {
        override val type: String = "email.member-activation"
    }

    object PasswordReset : RecoveryEmail(TokenPurpose.PASSWORD_RESET) {
        override val type: String = "email.password-reset"
    }

    object EmailChange : RecoveryEmail(TokenPurpose.EMAIL_CHANGE) {
        override val type: String = "email.email-change"
    }

    object TwoFactorReenrolment : RecoveryEmail(TokenPurpose.TWO_FACTOR_REENROLMENT) {
        override val type: String = "email.two-factor-reenrolment"
    }

    val recoveryEmails: List<RecoveryEmail> = listOf(AccountActivation, MemberActivation, PasswordReset, EmailChange, TwoFactorReenrolment)

    /** The email for a link of [purpose]; a purpose that is never mailed has none. */
    fun recoveryEmailFor(purpose: TokenPurpose): RecoveryEmail =
        requireNotNull(recoveryEmails.firstOrNull { it.purpose == purpose }) { "A $purpose link is never emailed" }

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

    /** The member a link is for, and the link's token; what the link is for is the job's type. */
    data class RecoveryPayload(
        val userId: Long,
        val token: String,
    )
}
