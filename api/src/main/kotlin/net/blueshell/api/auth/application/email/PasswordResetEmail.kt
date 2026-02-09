package net.blueshell.api.auth.application.email

import net.blueshell.api.user.domain.model.User
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

class PasswordResetEmail(recipient: User, token: String, frontendUrl: String, appUrl: String) :
    RecoveryEmail(recipient, token, frontendUrl, appUrl) {
    override val subject: String
        get() = "Reset Your Blueshell Account Password"

    override val markdownContent: String
        get() {
            val username = URLEncoder.encode(recipient.username, StandardCharsets.UTF_8)
            val token = URLEncoder.encode(this.token, StandardCharsets.UTF_8)
            val resetLink =
                String.format("%s/account/reset-password?username=%s&token=%s", frontendUrl, username, token)

            return String.format(
                """
                        Dear %s,
                        
                        We received a request to reset your account's password.
                        
                        If you requested this password reset, please click on [this link](%s) to create a new password.
                        
                        **Important security information:**
                        - This link will expire after 24 hours for your security
                        - If you didn't request this password reset, please ignore this email
                        - Your account remains secure and no changes have been made
                        - Never share this reset link with anyone
                        
                        If you continue to have issues accessing your account, please contact us via our [discord](https://discord.gg/dFam2yqXu7) or visit our [website](%s).
                        
                        Please do not reply to this email, as this is a generated email. Any responses will be ignored.
                        
                        Kind regards,
                        Blueshell Esports Security Team
                        
                        """.trimIndent(),
                recipient.fullName,
                resetLink,
                appUrl
            )
        }
}
