package net.blueshell.api.domain.auth.application.email

import net.blueshell.api.domain.user.persistence.User
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

class MemberActivationEmail(recipient: User, token: String, frontendUrl: String, appUrl: String) :
    RecoveryEmail(recipient, token, frontendUrl, appUrl) {
    override val subject: String
        get() = "Activate your Account"

    override val markdownContent: String
        get() {
            val token = URLEncoder.encode(this.token, StandardCharsets.UTF_8)
            val activationLink = String.format("%s/account/activate/member?token=%s", frontendUrl, token)

            return String.format(
                """
                        Dear %s,
                        
                        A member of the board of Blueshell has created an account on the website for you. This was done
                        for administrative purposes and you do not need to take any action. You can use [this link](%s)
                        to activate your account on the website which can be done within 7 days of receiving this email.
                        If you do not activate your account in time you can contact a member of the board, or send a
                        message in the SiteCie suggestion channel of our discord to have a new activation email sent to
                        you :)
                        
                        Kind regards,
                        Board of ESA Blueshell
                        
                        """.trimIndent(),
                recipient.fullName,
                activationLink
            )
        }

    override val replyTo: String
        get() = "board@blueshell.utwente.nl"
}
