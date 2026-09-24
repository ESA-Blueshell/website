package net.blueshell.api.auth.domain

import net.blueshell.api.shared.email.EmailContent
import net.blueshell.api.shared.enums.TokenPurpose
import net.blueshell.api.user.persistence.User
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * Email builder functions for user recovery and activation workflows.
 *
 * These functions build EmailContent DTOs that serve as Anti-Corruption Layer (ADR-019)
 * between the auth domain and the platform email system.
 */

fun createPasswordResetEmail(
    recipient: User,
    token: String,
    frontendUrl: String,
): EmailContent {
    val encodedToken = URLEncoder.encode(token, StandardCharsets.UTF_8)
    val resetLink = "$frontendUrl/account/reset-password#token=$encodedToken"

    val markdownContent =
        """
        Dear ${recipient.fullName},

        We received a request to reset your account's password.

        If you requested this password reset, please click on [this link]($resetLink) to create a new password.

        **Important security information:**
        - This link will expire after 24 hours for your security
        - If you didn't request this password reset, please ignore this email
        - Your account remains secure and no changes have been made
        - Never share this reset link with anyone

        If you continue to have issues accessing your account, please contact us via our [discord](https://discord.gg/dFam2yqXu7) or visit our [website]($frontendUrl).

        Please do not reply to this email, as this is a generated email. Any responses will be ignored.

        Kind regards,
        Blueshell Esports Security Team
        """.trimIndent()

    return EmailContent(
        recipientEmail = recipient.email,
        recipientName = recipient.fullName,
        subject = "Reset Your Blueshell Account Password",
        markdownContent = markdownContent,
    )
}

fun createUserActivationEmail(
    recipient: User,
    token: String,
    frontendUrl: String,
): EmailContent {
    val encodedToken = URLEncoder.encode(token, StandardCharsets.UTF_8)
    val activationLink = "$frontendUrl/account/activate/user#token=$encodedToken"

    val markdownContent =
        """
        Dear ${recipient.fullName},

        Thank you for signing up to the Blueshell website!

        You can activate your account by clicking on [this link]($activationLink).
        For information on events and our general community, check out our [discord](https://discord.gg/dFam2yqXu7) or [website]($frontendUrl).
        Enjoy your stay!

        Please do not reply to this email, as this is a generated email. Any responses will be ignored.

        Kind regards, <br/>
        Blueshell Esports
        """.trimIndent()

    return EmailContent(
        recipientEmail = recipient.email,
        recipientName = recipient.fullName,
        subject = "Activate your Account",
        markdownContent = markdownContent,
    )
}

fun createMemberActivationEmail(
    recipient: User,
    token: String,
    frontendUrl: String,
): EmailContent {
    val encodedToken = URLEncoder.encode(token, StandardCharsets.UTF_8)
    val activationLink = "$frontendUrl/account/activate/member#token=$encodedToken"

    val markdownContent =
        """
        Dear ${recipient.fullName},

        A member of the board of Blueshell has created an account on the website for you. This was done
        for administrative purposes and you do not need to take any action. You can use [this link]($activationLink)
        to activate your account on the website which can be done within 7 days of receiving this email.
        If you do not activate your account in time you can contact a member of the board, or send a
        message in the SiteCie suggestion channel of our discord to have a new activation email sent to
        you :)

        Kind regards,
        Board of ESA Blueshell
        """.trimIndent()

    return EmailContent(
        recipientEmail = recipient.email,
        recipientName = recipient.fullName,
        subject = "Activate your Account",
        markdownContent = markdownContent,
        replyToOverride = "board@blueshell.utwente.nl",
    )
}

fun createTwoFactorReenrolmentEmail(
    recipient: User,
    token: String,
    frontendUrl: String,
): EmailContent {
    val link = "$frontendUrl/account/re-enrol#token=${URLEncoder.encode(token, StandardCharsets.UTF_8)}"

    val markdownContent =
        """
        Dear ${recipient.fullName},

        An admin reset the two-factor authentication on your Blueshell account, after you asked for
        help getting back in. You have been signed out everywhere.

        To sign in again, open [this link]($link) and enter your username and password. Your password
        alone no longer gets you in. The link works once, for 24 hours; an admin can send a new one.

        If you did not ask for this, contact the board at board@blueshell.utwente.nl straight away.

        Kind regards,
        Board of ESA Blueshell
        """.trimIndent()

    return EmailContent(
        recipientEmail = recipient.email,
        recipientName = recipient.fullName,
        subject = "Sign in to set up two-factor again",
        markdownContent = markdownContent,
    )
}

fun createEmailChangeEmail(
    recipient: User,
    token: String,
    frontendUrl: String,
): EmailContent {
    val link = "$frontendUrl/account/confirm-email#token=${URLEncoder.encode(token, StandardCharsets.UTF_8)}"

    val markdownContent =
        """
        Dear ${recipient.fullName},

        You asked to move your Blueshell account to this email address. [Confirm it]($link) to finish;
        until you do, your account keeps its old address. The link works once, for 24 hours.

        If you did not ask for this, ignore this email and nothing changes.

        Kind regards,
        Board of ESA Blueshell
        """.trimIndent()

    return EmailContent(
        recipientEmail = requireNotNull(recipient.pendingEmail) { "No address is waiting to be confirmed" },
        recipientName = recipient.fullName,
        subject = "Confirm your new email address",
        markdownContent = markdownContent,
    )
}

/**
 * Stands in for the token in a preview. A recovery link is a credential, and one that
 * exists is one that can be used, so a preview issues nothing and renders this instead.
 */
const val PREVIEW_TOKEN_PLACEHOLDER: String = "PREVIEW-ONLY-NO-TOKEN-ISSUED"

/**
 * Selects the email a recovery purpose sends. Previewing and sending both come through
 * here, so a preview cannot show something other than what is delivered.
 */
fun buildRecoveryEmail(
    purpose: TokenPurpose,
    recipient: User,
    token: String,
    frontendUrl: String,
): EmailContent =
    when (purpose) {
        TokenPurpose.MEMBER_ACTIVATION -> createMemberActivationEmail(recipient, token, frontendUrl)
        TokenPurpose.USER_ACTIVATION -> createUserActivationEmail(recipient, token, frontendUrl)
        TokenPurpose.PASSWORD_RESET -> createPasswordResetEmail(recipient, token, frontendUrl)
        TokenPurpose.TWO_FACTOR_REENROLMENT -> createTwoFactorReenrolmentEmail(recipient, token, frontendUrl)
        TokenPurpose.EMAIL_CHANGE -> createEmailChangeEmail(recipient, token, frontendUrl)
        // A lock link travels inside the security notification that reports its event.
        TokenPurpose.ACCOUNT_LOCK -> throw IllegalArgumentException("A lock link is sent with a security notification")
        // Never emailed by design (ADR-024) — fail loudly rather than leak it.
        TokenPurpose.SIGNUP_CONTINUATION -> throw IllegalArgumentException(
            "A ${TokenPurpose.SIGNUP_CONTINUATION} token must never be emailed",
        )
    }
