package net.blueshell.api.domain.auth.application.email

import net.blueshell.api.domain.user.persistence.User
import net.blueshell.api.shared.email.EmailContent
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
    appUrl: String
): EmailContent {
    val username = URLEncoder.encode(recipient.username, StandardCharsets.UTF_8)
    val encodedToken = URLEncoder.encode(token, StandardCharsets.UTF_8)
    val resetLink = "$frontendUrl/account/reset-password?username=$username&token=$encodedToken"

    val markdownContent = """
        Dear ${recipient.fullName},

        We received a request to reset your account's password.

        If you requested this password reset, please click on [this link]($resetLink) to create a new password.

        **Important security information:**
        - This link will expire after 24 hours for your security
        - If you didn't request this password reset, please ignore this email
        - Your account remains secure and no changes have been made
        - Never share this reset link with anyone

        If you continue to have issues accessing your account, please contact us via our [discord](https://discord.gg/dFam2yqXu7) or visit our [website]($appUrl).

        Please do not reply to this email, as this is a generated email. Any responses will be ignored.

        Kind regards,
        Blueshell Esports Security Team
    """.trimIndent()

    return EmailContent(
        recipientEmail = recipient.email,
        recipientName = recipient.fullName,
        subject = "Reset Your Blueshell Account Password",
        markdownContent = markdownContent
    )
}

fun createUserActivationEmail(
    recipient: User,
    token: String,
    frontendUrl: String,
    appUrl: String
): EmailContent {
    val username = URLEncoder.encode(recipient.username, StandardCharsets.UTF_8)
    val encodedToken = URLEncoder.encode(token, StandardCharsets.UTF_8)
    val redirectEnc = URLEncoder.encode("/membership/signup", StandardCharsets.UTF_8)
    val activationLink = "$frontendUrl/account/activate/user?username=$username&token=$encodedToken&redirect=$redirectEnc"

    val markdownContent = """
        Dear ${recipient.fullName},

        Thank you for signing up to the Blueshell website!

        You can activate your account by clicking on [this link]($activationLink).
        For information on events and our general community, check out our [discord](https://discord.gg/dFam2yqXu7) or [website]($appUrl).
        Enjoy your stay!

        Please do not reply to this email, as this is a generated email. Any responses will be ignored.

        Kind regards, <br/>
        Blueshell Esports
    """.trimIndent()

    return EmailContent(
        recipientEmail = recipient.email,
        recipientName = recipient.fullName,
        subject = "Activate your Account",
        markdownContent = markdownContent
    )
}

fun createMemberActivationEmail(
    recipient: User,
    token: String,
    frontendUrl: String,
    appUrl: String
): EmailContent {
    val encodedToken = URLEncoder.encode(token, StandardCharsets.UTF_8)
    val activationLink = "$frontendUrl/account/activate/member?token=$encodedToken"

    val markdownContent = """
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
        replyTo = "board@blueshell.utwente.nl"
    )
}
