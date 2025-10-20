package net.blueshell.api.email.recovery;

import net.blueshell.api.base.BaseEmail;
import net.blueshell.api.model.User;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public class PasswordResetEmail extends RecoveryEmail {

    public PasswordResetEmail(User recipient, String token, String frontendUrl, String appUrl) {
        super(recipient, token, frontendUrl, appUrl);
    }

    @Override
    public String getSubject() {
        return "Reset Your Blueshell Account Password";
    }

    @Override
    public String getMarkdownContent() {
        String username = URLEncoder.encode(recipient.getUsername(), StandardCharsets.UTF_8);
        String token = URLEncoder.encode(getToken(), StandardCharsets.UTF_8);
        String resetLink = String.format("%s/account/reset-password?username=%s&token=%s", frontendUrl, username, token);

        return String.format(
                """
                        Dear %s,
                        
                        We received a request to reset your password for your Blueshell account.
                        
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
                        """,
                recipient.getFullName(),
                resetLink,
                appUrl
        );
    }
}
