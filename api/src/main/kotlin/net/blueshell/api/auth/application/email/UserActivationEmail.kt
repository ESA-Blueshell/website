package net.blueshell.api.auth.application.email

import net.blueshell.api.user.domain.model.User
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

class UserActivationEmail(
    recipient: User, token: String, frontendUrl: String, appUrl: String
) :
    RecoveryEmail(recipient, token, frontendUrl, appUrl) {
    override val subject: String
        get() = "Activate your Account"

    override val markdownContent: String
        get() {
            val username = URLEncoder.encode(recipient.username, StandardCharsets.UTF_8)
            val encodedToken = URLEncoder.encode(token, StandardCharsets.UTF_8)
            val redirectEnc = URLEncoder.encode("/membership/signup", StandardCharsets.UTF_8)

            val activationLink = String.format(
                "%s/account/activate/user?username=%s&token=%s&redirect=%s",
                frontendUrl, username, encodedToken, redirectEnc
            )
            return String.format(
                """
                        Dear %s,
                        
                        Thank you for signing up to the Blueshell website!
                        
                        You can activate your account by clicking on [this link](%s).
                        For information on events and our general community, check out our [discord](https://discord.gg/dFam2yqXu7) or [website](%s).
                        Enjoy your stay!
                        
                        Please do not reply to this email, as this is a generated email. Any responses will be ignored.
                        
                        Kind regards, <br/>
                        Blueshell Esports
                        
                        """.trimIndent(),
                recipient.fullName,
                activationLink,
                appUrl
            )
        }
}
